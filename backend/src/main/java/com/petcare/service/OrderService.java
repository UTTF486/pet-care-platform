package com.petcare.service;

import com.petcare.annotation.RateLimit;
import com.petcare.common.*;
import com.petcare.dto.OrderCreateDTO;
import com.petcare.dto.ReviewDTO;
import com.petcare.entity.Order;
import com.petcare.entity.OrderLog;
import com.petcare.entity.Message;
import com.petcare.entity.Pet;
import com.petcare.entity.Review;
import com.petcare.entity.ServiceItem;
import com.petcare.mapper.*;
import com.petcare.util.AppointmentTimeUtil;
import com.petcare.util.CommonUtil;
import com.petcare.util.OrderNoGenerator;
import com.petcare.util.PageUtil;
import com.petcare.util.RequestFingerprintUtil;
import com.petcare.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务：状态机 + 事务 + 日志 + 通知。
 * <p>
 * 状态机（只允许这 5 种状态迁移）：
 * 0 待接单 --接单--> 1 待服务 --完成--> 2 已完成
 * 0/1 --用户取消/商家拒单/超时--> 3 已取消
 * <p>
 * 并发安全的关键：所有流转都用 "UPDATE ... WHERE status = 旧状态"，
 * 影响行数为 0 就说明状态已经被别人改过，直接抛业务异常。
 * 条件更新避免引入分布式锁，并阻止同一订单被重复接单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String URGE_KEY_PREFIX = "urge:order:";
    /** 同一订单 5 分钟内只能催一次，防止骚扰商家 */
    private static final long URGE_INTERVAL_MINUTES = 5;

    private final OrderMapper orderMapper;
    private final OrderLogMapper orderLogMapper;
    private final PetMapper petMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServicerMapper servicerMapper;
    private final ReviewMapper reviewMapper;
    private final MessageService messageService;
    private final CommonUtil commonUtil;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderTimeoutService orderTimeoutService;
    private final StringRedisTemplate redisTemplate;

    // ==================== 用户端 ====================

    /**
     * 下单。
     * <p>
     * 金额、订单号、状态全部由后端计算，前端传来的只有"买什么、给谁、什么时候、送哪"。
     * 这一步是防"前端改价"的关键。
     */
    @RateLimit(key = "order:create", count = 5, seconds = 60, message = "下单太频繁了，休息一下再试")
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(OrderCreateDTO dto) {
        Long userId = commonUtil.currentUserId();
        if (!StringUtils.hasText(dto.getRequestId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请求号不能为空");
        }
        String requestId = dto.getRequestId().trim();
        String requestHash = RequestFingerprintUtil.of(userId, dto);

        // 幂等预查覆盖常见的重复点击；并发时再由 uk_user_request 兜底。
        Order existingOrder = orderMapper.selectByUserAndRequestId(userId, requestId);
        if (existingOrder != null) {
            requireSameRequestPayload(existingOrder, requestHash);
            return orderMapper.selectDetail(existingOrder.getId());
        }

        // 1. 锁定宠物行并校验归属；删除宠物也锁同一行，避免检查后插入新订单。
        Pet pet = petMapper.selectByIdForUpdate(dto.getPetId(), userId);
        if (pet == null) {
            throw new BusinessException("宠物档案不存在，请先添加宠物");
        }

        // 2. 校验服务：必须存在且上架
        ServiceItem service = serviceItemMapper.selectById(dto.getServiceId());
        if (service == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务项目不存在");
        }
        if (service.getStatus() == null || service.getStatus() != 1) {
            throw new BusinessException("该服务已下架，看看其他服务吧");
        }
        if (service.getDuration() == null || service.getDuration() <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服务时长配置异常");
        }

        // 3. 校验时段，并把日期 + 时段合成完整时间，拦截“今天已经过去的时段”
        if (!AppointmentTimeUtil.isValidSlot(dto.getTimeSlot())) {
            throw new BusinessException("预约时段不合法");
        }
        LocalDateTime appointmentStart =
                AppointmentTimeUtil.startAt(dto.getAppointDate(), dto.getTimeSlot());
        if (AppointmentTimeUtil.isStartPassed(appointmentStart, commonUtil.now())) {
            throw new BusinessException("该预约时段已经开始，请选择更晚的时段");
        }
        LocalDateTime appointmentEnd =
                AppointmentTimeUtil.endAt(dto.getAppointDate(), dto.getTimeSlot(), service.getDuration());

        // 4. 组装订单：价格与服务名做快照
        Order order = new Order();
        order.setRequestId(requestId);
        order.setRequestHash(requestHash);
        order.setUserId(userId);
        order.setPetId(pet.getId());
        order.setServiceId(service.getId());
        order.setServiceName(service.getName());
        order.setServicePrice(service.getPrice());
        order.setAmount(service.getPrice());
        order.setServiceDuration(service.getDuration());
        order.setContactName(dto.getContactName());
        order.setContactPhone(dto.getContactPhone());
        order.setAddress(dto.getAddress());
        order.setAppointDate(dto.getAppointDate());
        order.setTimeSlot(dto.getTimeSlot());
        order.setAppointmentStart(appointmentStart);
        order.setAppointmentEnd(appointmentEnd);
        order.setStatus(OrderStatus.PENDING_ACCEPT);
        order.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "");
        // 超时时间 = 现在 + 配置的分钟数，定时任务靠它扫单
        order.setExpireTime(commonUtil.buildExpireTime());

        Long effectiveOrderId = insertOrderWithRetry(order);
        if (!effectiveOrderId.equals(order.getId())) {
            // 并发重复请求命中了幂等唯一键，返回原订单，不重复写日志和消息。
                return orderMapper.selectDetail(effectiveOrderId);
        }

        // 5. 记流转日志 + 给用户发通知（在同一个事务里，要么都成功要么都回滚）
        saveLog(order.getId(), null, OrderStatus.PENDING_ACCEPT, 0, userId,
                "ORDER_CREATED", "用户提交预约");
        messageService.send(userId, order.getId(), "ORDER", "预约提交成功",
                "订单 " + order.getOrderNo() + " 已提交，等待商家接单");
        messageService.send(Message.ADMIN_USER_ID, order.getId(), "ORDER", "有新订单待接单",
                "订单 " + order.getOrderNo() + " 已提交，请尽快处理");

        log.info("用户 {} 下单成功, orderNo={}, amount={}", userId, order.getOrderNo(), order.getAmount());
        return orderMapper.selectDetail(order.getId());
    }

    public PageResult<OrderVO> myOrders(Integer status, int pageNum, int pageSize) {
        Long userId = commonUtil.currentUserId();
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        int offset = PageUtil.offset(pageNum, pageSize);
        long total = orderMapper.countUser(userId, status);
        if (total == 0) {
            return PageResult.empty(pageNum, pageSize);
        }
        List<OrderVO> list = orderMapper.selectUserPage(userId, status, offset, pageSize);
        return PageResult.of(total, pageNum, pageSize, list);
    }

    /**
     * 订单详情：普通用户只能看自己的，管理员可以看全部。
     */
    public OrderVO detail(Long id) {
        OrderVO order = orderMapper.selectDetail(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!UserContext.isAdmin() && !order.getUserId().equals(commonUtil.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return order;
    }

    /** 订单流转日志：复用详情接口的归属校验。 */
    public List<OrderLog> logs(Long id) {
        detail(id);
        return orderLogMapper.selectByOrderId(id);
    }

    /** 当前订单评价；没有评价时返回 null。 */
    public Review reviewDetail(Long id) {
        detail(id);
        return reviewMapper.selectByOrderId(id);
    }

    /**
     * 用户取消订单：只有"待接单/待服务"可以取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        Long userId = commonUtil.currentUserId();
        Order order = orderMapper.selectState(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == OrderStatus.FINISHED) {
            throw new BusinessException("服务已完成，无法取消");
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessException("订单已取消，请勿重复操作");
        }

        String finalReason = normalizeReason(reason, "用户主动取消");
        int rows = orderMapper.cancel(id, finalReason);
        if (rows == 0) {
            // 说明这一瞬间订单状态被别人改了（比如商家刚好完成），提示刷新
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        saveLog(id, order.getStatus(), OrderStatus.CANCELED, 0, userId,
                "STATUS_CHANGE", finalReason);
        messageService.send(userId, id, "ORDER", "订单已取消",
                "订单 " + order.getOrderNo() + " 已取消，原因：" + finalReason);
        // 通知商家，让后台知道少了一单
        messageService.send(Message.ADMIN_USER_ID, id, "ORDER", "用户取消订单",
                "订单 " + order.getOrderNo() + " 被用户取消：" + finalReason);
    }

    /**
     * 用户催单：Redis 做 5 分钟去重，避免用户连点。
     */
    @Transactional(rollbackFor = Exception.class)
    public void urge(Long id) {
        Long userId = commonUtil.currentUserId();
        Order order = orderMapper.selectState(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BusinessException("订单已被接单，无需催单");
        }

        String key = URGE_KEY_PREFIX + id;
        // setIfAbsent = SETNX，只有 key 不存在时才写入成功
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", URGE_INTERVAL_MINUTES, TimeUnit.MINUTES);
        if (first == null || !first) {
            throw new BusinessException("已经催过啦，商家正在尽快安排");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        deleteUrgeKeyQuietly(id, key);
                    }
                }
            });
        }

        try {
            saveLog(id, order.getStatus(), order.getStatus(), 0, userId, "URGED", "用户催单");
            messageService.send(Message.ADMIN_USER_ID, id, "URGE", "用户催单",
                    "订单 " + order.getOrderNo() + " 用户已催单，请尽快接单");
        } catch (RuntimeException e) {
            deleteUrgeKeyQuietly(id, key);
            throw e;
        }
    }

    /**
     * 提交评价：一单一评，只有已完成订单可评。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(ReviewDTO dto) {
        Long userId = commonUtil.currentUserId();
        OrderVO order = orderMapper.selectDetail(dto.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != OrderStatus.FINISHED) {
            throw new BusinessException("服务完成后才能评价");
        }
        if (Boolean.TRUE.equals(order.getReviewed())) {
            throw new BusinessException("该订单已评价过了");
        }

        Review review = new Review();
        review.setOrderId(order.getId());
        review.setUserId(userId);
        review.setServiceId(order.getServiceId());
        review.setServicerId(order.getServicerId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        try {
            // 唯一索引 uk_order_id 兜底并发重复提交
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该订单已评价过了，请勿重复提交");
        }
        if (order.getServicerId() != null) {
            servicerMapper.refreshRatingFromReviews(order.getServicerId());
        }
        saveLog(order.getId(), OrderStatus.FINISHED, OrderStatus.FINISHED, 0, userId,
                "REVIEWED", "用户提交评价");

        messageService.send(userId, order.getId(), "ORDER", "评价成功",
                "感谢你的反馈，我们会持续改进服务");
    }

    // ==================== 后台 ====================

    public PageResult<OrderVO> adminPage(Integer status, String keyword, int pageNum, int pageSize) {
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        int offset = PageUtil.offset(pageNum, pageSize);
        long total = orderMapper.countAdmin(status, keyword);
        if (total == 0) {
            return PageResult.empty(pageNum, pageSize);
        }
        List<OrderVO> list = orderMapper.selectAdminPage(status, keyword, offset, pageSize);
        return PageResult.of(total, pageNum, pageSize, list);
    }

    /**
     * 商家接单（后台的"订单审核"）：待接单 -> 待服务，并指派服务师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, Long servicerId) {
        Order order = orderMapper.selectState(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BusinessException("只有待接单的订单才能接单");
        }
        // 锁住服务师行，使同一服务师的接单检查串行执行；随后用锁定读检查时段冲突。
        if (servicerMapper.lockAvailableById(servicerId) == null) {
            throw new BusinessException("服务师不存在或当前休息中");
        }
        OrderInterval interval = resolveOrderInterval(order);
        if (AppointmentTimeUtil.isStartPassed(interval.start, commonUtil.now())) {
            throw new BusinessException("预约时段已经开始，不能接单，请联系用户改约或拒单");
        }
        List<Long> conflicts = orderMapper.selectServicerScheduleConflicts(
                servicerId, interval.start, interval.end, id);
        if (!conflicts.isEmpty()) {
            throw new BusinessException("该服务师在预约时段已有待服务订单，请换人或调整时段");
        }

        int rows = orderMapper.accept(id, servicerId);
        if (rows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        servicerMapper.increaseOrderCount(servicerId);

        saveLog(id, OrderStatus.PENDING_ACCEPT, OrderStatus.PENDING_SERVICE, 1,
                commonUtil.currentUserId(), "STATUS_CHANGE", "商家接单并指派服务师");
        messageService.send(order.getUserId(), id, "ORDER", "商家已接单",
                "订单 " + order.getOrderNo() + " 已安排服务师，请保持电话畅通");
    }

    /**
     * 完成服务：待服务 -> 已完成，同时累加服务销量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        Order order = orderMapper.selectState(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_SERVICE) {
            throw new BusinessException("只有待服务的订单才能完成");
        }

        int rows = orderMapper.finish(id);
        if (rows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        serviceItemMapper.increaseSales(order.getServiceId());

        saveLog(id, OrderStatus.PENDING_SERVICE, OrderStatus.FINISHED, 1,
                commonUtil.currentUserId(), "STATUS_CHANGE", "服务完成");
        messageService.send(order.getUserId(), id, "ORDER", "服务已完成",
                "订单 " + order.getOrderNo() + " 已完成，快去评价吧～");
    }

    /**
     * 商家拒单：待接单 -> 已取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        Order order = orderMapper.selectState(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BusinessException("只有待接单的订单才能拒单");
        }
        String finalReason = normalizeReason(reason, "商家暂时无法提供服务");
        int rows = orderMapper.cancel(id, finalReason);
        if (rows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        saveLog(id, OrderStatus.PENDING_ACCEPT, OrderStatus.CANCELED, 1,
                commonUtil.currentUserId(), "STATUS_CHANGE", finalReason);
        messageService.send(order.getUserId(), id, "ORDER", "订单已取消",
                "订单 " + order.getOrderNo() + " 商家无法接单，已取消：" + finalReason);
    }

    // ==================== 定时任务入口 ====================

    /**
     * 扫描超时订单并批量取消。
     * 由 OrderTimeoutTask 每分钟调用一次。
     *
     * @return 本轮取消的订单数
     */
    public int cancelTimeoutOrders(int batchSize) {
        List<Order> timeoutList = orderMapper.selectTimeoutList(batchSize);
        int canceled = 0;
        for (Order order : timeoutList) {
            try {
                // 每单在独立事务中执行，一单失败不会回滚已经处理的其他订单。
                if (orderTimeoutService.cancelOne(order)) {
                    canceled++;
                }
            } catch (Exception e) {
                log.error("超时订单取消失败, orderId={}, orderNo={}",
                        order.getId(), order.getOrderNo(), e);
            }
        }
        return canceled;
    }

    /**
     * 订单号使用时间 + 高熵后缀；数据库唯一索引仍是最后一道兜底，冲突时自动换号重试。
     */
    private Long insertOrderWithRetry(Order order) {
        final int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            order.setOrderNo(orderNoGenerator.next());
            try {
                orderMapper.insert(order);
                return order.getId();
            } catch (DuplicateKeyException e) {
                Order existingOrder = orderMapper.selectByUserAndRequestIdForUpdate(
                        order.getUserId(), order.getRequestId());
                if (existingOrder != null) {
                    requireSameRequestPayload(existingOrder, order.getRequestHash());
                    return existingOrder.getId();
                }
                log.warn("订单号冲突，准备重试: orderNo={}, attempt={}",
                        order.getOrderNo(), attempt);
            }
        }
        throw new BusinessException(ResultCode.ERROR, "订单号生成冲突，请稍后重试");
    }

    private OrderInterval resolveOrderInterval(Order order) {
        if (order.getAppointmentStart() != null && order.getAppointmentEnd() != null) {
            return new OrderInterval(order.getAppointmentStart(), order.getAppointmentEnd());
        }
        Integer duration = order.getServiceDuration();
        if (duration == null) {
            ServiceItem service = serviceItemMapper.selectById(order.getServiceId());
            duration = service == null ? null : service.getDuration();
        }
        if (duration == null || duration <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订单服务时长缺失");
        }
        return new OrderInterval(
                AppointmentTimeUtil.startAt(order.getAppointDate(), order.getTimeSlot()),
                AppointmentTimeUtil.endAt(order.getAppointDate(), order.getTimeSlot(), duration));
    }

    private static class OrderInterval {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private OrderInterval(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    private String normalizeReason(String reason, String defaultReason) {
        String value = StringUtils.hasText(reason) ? reason.trim() : defaultReason;
        if (value.length() > 255) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "取消原因最多 255 个字符");
        }
        return value;
    }

    private void deleteUrgeKeyQuietly(Long orderId, String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception cleanupError) {
            log.warn("催单失败后清理 Redis 去重 key 失败, orderId={}", orderId, cleanupError);
        }
    }

    private void requireSameRequestPayload(Order existingOrder, String requestHash) {
        if (!requestHash.equals(existingOrder.getRequestHash())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "同一请求号对应了不同的下单内容，请重新生成请求号");
        }
    }

    /**
     * 写流转日志。fromStatus 允许为空（首次创建）。
     */
    private void saveLog(Long orderId, Integer fromStatus, Integer toStatus,
                         Integer operatorType, Long operatorId, String eventType, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(orderId);
        log.setFromStatus(fromStatus == null ? toStatus : fromStatus);
        log.setToStatus(toStatus);
        log.setEventType(eventType);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        orderLogMapper.insert(log);
    }
}

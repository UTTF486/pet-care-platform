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
import com.petcare.util.CommonUtil;
import com.petcare.util.PageUtil;
import com.petcare.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务：状态机 + 事务 + 日志 + 通知。
 * <p>
 * 状态机（只允许这 4 条边）：
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

    /** 可选预约时段，后端再校验一次，防止前端绕过页面直接调接口 */
    private static final List<String> TIME_SLOTS = Arrays.asList(
            "08:00-10:00", "10:00-12:00", "14:00-16:00", "16:00-18:00", "18:00-20:00");

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

        // 1. 校验宠物归属：只能给自己的宠物下单
        Pet pet = petMapper.selectById(dto.getPetId());
        if (pet == null || !pet.getUserId().equals(userId)) {
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

        // 3. 校验时段
        if (!TIME_SLOTS.contains(dto.getTimeSlot())) {
            throw new BusinessException("预约时段不合法");
        }

        // 4. 组装订单：价格与服务名做快照
        Order order = new Order();
        order.setOrderNo(commonUtil.generateOrderNo());
        order.setUserId(userId);
        order.setPetId(pet.getId());
        order.setServiceId(service.getId());
        order.setServiceName(service.getName());
        order.setServicePrice(service.getPrice());
        order.setAmount(service.getPrice());
        order.setContactName(dto.getContactName());
        order.setContactPhone(dto.getContactPhone());
        order.setAddress(dto.getAddress());
        order.setAppointDate(dto.getAppointDate());
        order.setTimeSlot(dto.getTimeSlot());
        order.setStatus(OrderStatus.PENDING_ACCEPT);
        order.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "");
        // 超时时间 = 现在 + 配置的分钟数，定时任务靠它扫单
        order.setExpireTime(commonUtil.buildExpireTime());

        orderMapper.insert(order);

        // 5. 记流转日志 + 给用户发通知（在同一个事务里，要么都成功要么都回滚）
        saveLog(order.getId(), null, OrderStatus.PENDING_ACCEPT, 0, userId, "用户提交预约");
        messageService.send(userId, order.getId(), "ORDER", "预约提交成功",
                "订单 " + order.getOrderNo() + " 已提交，等待商家接单");

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

    /**
     * 用户取消订单：只有"待接单/待服务"可以取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        Long userId = commonUtil.currentUserId();
        Order order = orderMapper.selectDetail(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == OrderStatus.FINISHED) {
            throw new BusinessException("服务已完成，无法取消");
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessException("订单已取消，请勿重复操作");
        }

        String finalReason = StringUtils.hasText(reason) ? reason : "用户主动取消";
        int rows = orderMapper.cancel(id, finalReason);
        if (rows == 0) {
            // 说明这一瞬间订单状态被别人改了（比如商家刚好完成），提示刷新
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        saveLog(id, order.getStatus(), OrderStatus.CANCELED, 0, userId, finalReason);
        messageService.send(userId, id, "ORDER", "订单已取消",
                "订单 " + order.getOrderNo() + " 已取消，原因：" + finalReason);
        // 通知商家，让后台知道少了一单
        messageService.send(Message.ADMIN_USER_ID, id, "ORDER", "用户取消订单",
                "订单 " + order.getOrderNo() + " 被用户取消：" + finalReason);
    }

    /**
     * 用户催单：Redis 做 5 分钟去重，避免用户连点。
     */
    public void urge(Long id) {
        Long userId = commonUtil.currentUserId();
        Order order = orderMapper.selectDetail(id);
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

        saveLog(id, order.getStatus(), order.getStatus(), 0, userId, "用户催单");
        messageService.send(Message.ADMIN_USER_ID, id, "URGE", "用户催单",
                "订单 " + order.getOrderNo() + " 用户已催单，请尽快接单");
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
        // 唯一索引 uk_order_id 兜底，重复提交会抛 DuplicateKeyException 被全局异常捕获
        reviewMapper.insert(review);

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
        Order order = orderMapper.selectDetail(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BusinessException("只有待接单的订单才能接单");
        }
        if (servicerMapper.countAvailableById(servicerId) == 0) {
            throw new BusinessException("服务师不存在或当前休息中");
        }

        int rows = orderMapper.accept(id, servicerId);
        if (rows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        servicerMapper.increaseOrderCount(servicerId);

        saveLog(id, OrderStatus.PENDING_ACCEPT, OrderStatus.PENDING_SERVICE, 1,
                commonUtil.currentUserId(), "商家接单并指派服务师");
        messageService.send(order.getUserId(), id, "ORDER", "商家已接单",
                "订单 " + order.getOrderNo() + " 已安排服务师，请保持电话畅通");
    }

    /**
     * 完成服务：待服务 -> 已完成，同时累加服务销量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        Order order = orderMapper.selectDetail(id);
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
                commonUtil.currentUserId(), "服务完成");
        messageService.send(order.getUserId(), id, "ORDER", "服务已完成",
                "订单 " + order.getOrderNo() + " 已完成，快去评价吧～");
    }

    /**
     * 商家拒单：待接单 -> 已取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        Order order = orderMapper.selectDetail(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BusinessException("只有待接单的订单才能拒单");
        }
        String finalReason = StringUtils.hasText(reason) ? reason : "商家暂时无法提供服务";
        int rows = orderMapper.cancel(id, finalReason);
        if (rows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        saveLog(id, OrderStatus.PENDING_ACCEPT, OrderStatus.CANCELED, 1,
                commonUtil.currentUserId(), finalReason);
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
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders(int batchSize) {
        List<Order> timeoutList = orderMapper.selectTimeoutList(batchSize);
        int canceled = 0;
        for (Order order : timeoutList) {
            // 只允许 0 -> 3，防止把刚被接单的订单误取消
            int rows = orderMapper.cancelTimeout(order.getId(), "超时未接单，系统自动取消");
            if (rows == 0) {
                continue;
            }
            saveLog(order.getId(), OrderStatus.PENDING_ACCEPT, OrderStatus.CANCELED, 2, 0L, "系统超时自动取消");
            messageService.send(order.getUserId(), order.getId(), "ORDER", "订单已自动取消",
                    "订单 " + order.getOrderNo() + " 超时未接单，已自动取消，可重新预约");
            canceled++;
        }
        return canceled;
    }

    /**
     * 写流转日志。fromStatus 允许为空（首次创建）。
     */
    private void saveLog(Long orderId, Integer fromStatus, Integer toStatus,
                         Integer operatorType, Long operatorId, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(orderId);
        log.setFromStatus(fromStatus == null ? toStatus : fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        orderLogMapper.insert(log);
    }
}

package com.petcare.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.BusinessException;
import com.petcare.common.PageResult;
import com.petcare.common.ResultCode;
import com.petcare.entity.Category;
import com.petcare.entity.ServiceItem;
import com.petcare.mapper.CategoryMapper;
import com.petcare.mapper.ServiceItemMapper;
import com.petcare.util.PageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 服务品类 + 服务项目服务。
 * <p>
 * Redis 缓存策略：
 * 1. 缓存对象：热门服务列表、服务详情 —— 读多写极少，适合缓存
 * 2. 缓存模式：Cache-Aside（读时先查缓存，未命中查库并回写；写时删缓存）
 * 3. 写操作删除缓存，避免并发更新写入旧值
 * 4. 过期时间：热门 10 分钟、详情 30 分钟，避免缓存与库长期不一致
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private static final String KEY_HOT = "cache:service:hot";
    private static final String KEY_DETAIL_PREFIX = "cache:service:detail:";
    private final ServiceItemMapper serviceItemMapper;
    private final CategoryMapper categoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pet.cache.hot-service-seconds:600}")
    private long hotSeconds;

    @Value("${pet.cache.service-detail-seconds:1800}")
    private long detailSeconds;

    public List<Category> listCategories() {
        return categoryMapper.selectAllEnabled();
    }

    // ---------------- 用户端 ----------------

    /**
     * 热门服务：首页展示，命中缓存时完全不碰数据库。
     */
    public List<ServiceItem> listHot() {
        try {
            String cached = redisTemplate.opsForValue().get(KEY_HOT);
            if (cached != null) {
                log.debug("热门服务命中缓存");
                return objectMapper.readValue(cached, new TypeReference<List<ServiceItem>>() {
                });
            }
        } catch (Exception e) {
            // 缓存异常绝不能影响主流程：降级为直接查库
            log.warn("读取热门服务缓存失败，降级查库", e);
        }

        List<ServiceItem> list = serviceItemMapper.selectHot(6);
        writeCache(KEY_HOT, list, hotSeconds);
        return list;
    }

    /**
     * 用户端服务列表：只返回上架的。
     */
    public PageResult<ServiceItem> pageForUser(Long categoryId, String keyword, int pageNum, int pageSize) {
        return doPage(categoryId, keyword, 1, pageNum, pageSize);
    }

    /**
     * 服务详情：带缓存。
     */
    public ServiceItem detail(Long id) {
        String key = KEY_DETAIL_PREFIX + id;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("服务详情命中缓存, id={}", id);
                return objectMapper.readValue(cached, ServiceItem.class);
            }
        } catch (Exception e) {
            log.warn("读取服务详情缓存失败，降级查库", e);
        }

        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务项目不存在");
        }
        writeCache(key, item, detailSeconds);
        return item;
    }

    // ---------------- 后台管理 ----------------

    public PageResult<ServiceItem> pageForAdmin(Long categoryId, String keyword, Integer status,
                                                int pageNum, int pageSize) {
        return doPage(categoryId, keyword, status, pageNum, pageSize);
    }

    public Long save(ServiceItem item) {
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        if (item.getDuration() == null) {
            item.setDuration(60);
        }
        item.setCover(item.getCover() == null ? "" : item.getCover());
        item.setDescription(item.getDescription() == null ? "" : item.getDescription());
        serviceItemMapper.insert(item);
        evictCache(item.getId());
        return item.getId();
    }

    public void update(ServiceItem item) {
        if (item.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少服务 id");
        }
        int rows = serviceItemMapper.update(item);
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务项目不存在");
        }
        evictCache(item.getId());
    }

    /**
     * 上架/下架。下架后用户端立刻查不到，但历史订单不受影响（订单存了快照）。
     */
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值只能是 0 或 1");
        }
        serviceItemMapper.updateStatus(id, status);
        evictCache(id);
    }

    // ---------------- 私有方法 ----------------

    private PageResult<ServiceItem> doPage(Long categoryId, String keyword, Integer status,
                                           int pageNum, int pageSize) {
        pageNum = PageUtil.normalizePageNum(pageNum);
        pageSize = PageUtil.normalizePageSize(pageSize);
        int offset = PageUtil.offset(pageNum, pageSize);
        long total = serviceItemMapper.countList(categoryId, keyword, status);
        if (total == 0) {
            return PageResult.empty(pageNum, pageSize);
        }
        List<ServiceItem> list = serviceItemMapper.selectList(categoryId, keyword, status, offset, pageSize);
        return PageResult.of(total, pageNum, pageSize, list);
    }

    private void writeCache(String key, Object value, long seconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写缓存失败, key={}", key, e);
        }
    }

    /**
     * 服务数据变更后只删除受影响的 key，避免 KEYS 全库扫描阻塞 Redis。
     */
    private void evictCache(Long serviceId) {
        List<String> keys = new ArrayList<>(2);
        keys.add(KEY_HOT);
        if (serviceId != null) {
            keys.add(KEY_DETAIL_PREFIX + serviceId);
        }
        try {
            redisTemplate.delete(keys);
            log.debug("清理服务缓存 {} 个", keys.size());
        } catch (Exception e) {
            log.warn("清理服务缓存失败, keys={}", keys, e);
        }
    }
}

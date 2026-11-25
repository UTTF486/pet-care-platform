package com.petcare.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器：PC + 毫秒时间 + 12 位 Base36 高熵后缀。
 * 不依赖 Redis 序列，Redis 重启或 key 被清除不会让订单号从头计数。
 * 数据库唯一索引仍是最终防线，冲突时由 OrderService 重新生成并有限重试。
 */
@Component
public class OrderNoGenerator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final long ENTROPY_MASK = (1L << 61) - 1;

    private final SecureRandom secureRandom = new SecureRandom();

    public String next() {
        return build(LocalDateTime.now(BUSINESS_ZONE), secureRandom.nextLong() & ENTROPY_MASK);
    }

    /**
     * 纯函数便于测试：格式固定为 31 位，小于 t_order.order_no 的 32 位上限。
     */
    static String build(LocalDateTime now, long entropy) {
        if (now == null || entropy < 0) {
            throw new IllegalArgumentException("订单号参数不合法");
        }
        String suffix = Long.toString(entropy & ENTROPY_MASK, 36);
        StringBuilder padded = new StringBuilder(12);
        for (int i = suffix.length(); i < 12; i++) {
            padded.append('0');
        }
        padded.append(suffix);
        return "PC" + now.format(TIME_FORMAT) + padded;
    }
}

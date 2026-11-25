package com.petcare.util;

import com.petcare.dto.OrderCreateDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 下单幂等请求指纹。同一 requestId 携带不同业务参数时必须拒绝，避免返回旧订单。
 */
public final class RequestFingerprintUtil {

    private RequestFingerprintUtil() {
    }

    public static String of(Long userId, OrderCreateDTO dto) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, userId);
        appendField(canonical, dto.getPetId());
        appendField(canonical, dto.getServiceId());
        appendField(canonical, dto.getAppointDate());
        appendField(canonical, dto.getTimeSlot());
        appendField(canonical, dto.getContactName());
        appendField(canonical, dto.getContactPhone());
        appendField(canonical, dto.getAddress());
        appendField(canonical, dto.getRemark());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 使用长度前缀消除字段分隔符歧义，例如 `A|B + C` 和 `A + B|C` 不再冲突。
     */
    private static void appendField(StringBuilder target, Object value) {
        String normalized = normalize(value == null ? null : value.toString());
        target.append(normalized.length()).append(':').append(normalized).append(';');
    }
}

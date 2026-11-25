package com.petcare.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 只在直连地址属于可信代理时读取 X-Forwarded-For。
 * 没有可信代理配置时使用 remoteAddr，避免客户端伪造 forwarded header。
 */
@Component
@Slf4j
public class ClientIpResolver {

    private final List<CidrRange> trustedRanges;

    public ClientIpResolver(
            @Value("${pet.proxy.trusted-addresses:127.0.0.1/32,::1/128}") String rawTrustedAddresses) {
        List<CidrRange> ranges = new ArrayList<>();
        Arrays.stream(rawTrustedAddresses.split("\\s*,\\s*"))
                .filter(StringUtils::hasText)
                .map(this::parseRange)
                .filter(range -> range != null)
                .forEach(ranges::add);
        this.trustedRanges = ranges;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeAddress(request.getRemoteAddr());
        if (remoteAddress == null || !isTrusted(remoteAddress)) {
            return remoteAddress == null ? "unknown" : remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwardedFor)) {
            forwardedFor = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(forwardedFor)) {
            return remoteAddress;
        }

        // 从右往左找第一个非可信代理，忽略客户端可伪造的左侧历史值。
        String[] hops = forwardedFor.split("\\s*,\\s*");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalizeAddress(hops[i]);
            if (hop == null) {
                // 格式异常时不采信整条 forwarded 链，退回可信代理地址。
                return remoteAddress;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return remoteAddress;
    }

    private boolean isTrusted(String address) {
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress();
            for (CidrRange range : trustedRanges) {
                if (range.contains(bytes)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            logIgnoredAddress(address);
        }
        return false;
    }

    private CidrRange parseRange(String raw) {
        String[] parts = raw.trim().split("/", 2);
        String normalized = normalizeAddress(parts[0]);
        if (normalized == null) {
            return null;
        }
        try {
            byte[] bytes = InetAddress.getByName(normalized).getAddress();
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bytes.length * 8;
            if (prefix < 0 || prefix > bytes.length * 8) {
                return null;
            }
            return new CidrRange(bytes, prefix);
        } catch (Exception e) {
            return null;
        }
    }

    private void logIgnoredAddress(String address) {
        log.warn("忽略无法解析的 IP 地址: {}", address);
    }

    private String normalizeAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String value = address.trim();
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing > 1) {
                value = value.substring(1, closing);
            }
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("::ffff:") && isValidIpv4(lower.substring(7))) {
            return lower.substring(7);
        }
        if (isValidIpv4(lower)) {
            return lower;
        }
        if (lower.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}:\\d{1,5}$")) {
            String ipv4 = lower.substring(0, lower.lastIndexOf(':'));
            return isValidIpv4(ipv4) ? ipv4 : null;
        }
        if (!lower.matches("^[0-9a-f:.]+$") || !lower.contains(":")) {
            return null;
        }
        try {
            return InetAddress.getByName(lower).getHostAddress().toLowerCase();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private boolean isValidIpv4(String value) {
        if (!value.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) {
            return false;
        }
        for (String part : value.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static class CidrRange {
        private final byte[] network;
        private final int prefix;

        private CidrRange(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        private boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int remaining = prefix;
            for (int i = 0; i < candidate.length; i++) {
                if (remaining <= 0) {
                    return true;
                }
                int mask = remaining >= 8 ? 0xff : (0xff << (8 - remaining)) & 0xff;
                if ((candidate[i] & mask) != (network[i] & mask)) {
                    return false;
                }
                remaining -= 8;
            }
            return true;
        }
    }
}

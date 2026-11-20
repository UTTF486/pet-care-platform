package com.petcare.util;

import com.petcare.common.BusinessException;
import com.petcare.common.LoginUser;
import com.petcare.common.ResultCode;
import com.petcare.common.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一些零散工具方法。放一起，减少类爆炸。
 */
@Component
public class CommonUtil {

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${pet.order.timeout-minutes:30}")
    private int timeoutMinutes;

    /**
     * 生成业务订单号：PC + 秒级时间 + 4 位随机数。
     * 数据库还有唯一索引兜底，极端重复会直接报错而不是写脏数据。
     */
    public String generateOrderNo() {
        return "PC" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** 待接单订单的自动取消时间 */
    public LocalDateTime buildExpireTime() {
        return LocalDateTime.now().plusMinutes(timeoutMinutes);
    }

    /** 取当前登录用户 id，未登录直接抛 401 */
    public Long currentUserId() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.getId();
    }

    /** 校验当前用户是管理员 */
    public void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}

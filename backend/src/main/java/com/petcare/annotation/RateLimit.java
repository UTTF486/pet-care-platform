package com.petcare.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 接口限流注解。
 * 用法：@RateLimit(key = "order:create", count = 5, seconds = 60)
 * 含义：同一用户 60 秒内最多调用 5 次。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 业务标识，会拼进 Redis key，不同接口要唯一 */
    String key();

    /** 时间窗口内允许的最大次数 */
    int count() default 10;

    /** 时间窗口，单位秒 */
    int seconds() default 60;

    /** 触发限流时的提示语 */
    String message() default "操作太频繁了，请稍后再试";
}

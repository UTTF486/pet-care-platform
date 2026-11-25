package com.petcare.common;

/**
 * 当前请求的登录用户，基于 ThreadLocal。
 * <p>
 * 原理：Tomcat 每个请求一个线程，拦截器在 preHandle 里 set，请求结束后 afterCompletion 里 clear。
 * 业务层就不用一层层传 userId 参数了。
 * <p>
 * 请求结束后必须 clear，避免线程池复用导致用户信息串号。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getId();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

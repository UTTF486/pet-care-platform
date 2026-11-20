package com.petcare.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.common.LoginUser;
import com.petcare.common.Result;
import com.petcare.common.ResultCode;
import com.petcare.common.UserContext;
import com.petcare.util.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 登录 + 角色校验拦截器。
 * <p>
 * 流程：
 * 1. 放行预检请求 OPTIONS（否则浏览器跨域直接失败）
 * 2. 从 Header 取 Authorization: Bearer xxx
 * 3. 去 Redis 换出登录用户：拿不到 -> 401
 * 4. 访问 /api/admin/** 但不是管理员 -> 403
 * 5. 通过后把用户放进 ThreadLocal，并给 Token 续期
 * <p>
 * 说明：这里返回 HTTP 200 + body.code=401，而不是 HTTP 401，
 * 让前端只需处理业务 code 一种分支，避免 axios 走 error 回调时拿不到提示语。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 2. 用户端服务浏览类接口允许游客访问（首页数据要能直接看）
        if (isPublicApi(request)) {
            // 若带了 Token 也顺便解析，方便后续接口做个性化，不强制
            parseTokenQuietly(request);
            return true;
        }

        // 3. 取 Token
        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
        }
        LoginUser loginUser = tokenService.getLoginUser(token);
        if (loginUser == null) {
            writeJson(response, Result.fail(ResultCode.UNAUTHORIZED));
            return false;
        }

        // 4. 后台接口校验角色
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/admin") && !loginUser.isAdmin()) {
            writeJson(response, Result.fail(ResultCode.FORBIDDEN));
            return false;
        }

        // 5. 放入上下文 + 续期
        UserContext.set(loginUser);
        tokenService.refresh(token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理，防止线程复用导致用户串号
        UserContext.clear();
    }

    /**
     * 哪些接口不需要登录：
     * 登录、注册、服务列表、服务详情、品类列表（游客也能看）。
     */
    private boolean isPublicApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isGet = "GET".equalsIgnoreCase(request.getMethod());
        return isGet && (uri.equals("/api/services")
                || uri.equals("/api/services/hot")
                || uri.equals("/api/categories")
                || uri.matches("/api/services/\\d+"));
    }

    private void parseTokenQuietly(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && token.startsWith(TOKEN_PREFIX)) {
            LoginUser user = tokenService.getLoginUser(token.substring(TOKEN_PREFIX.length()));
            if (user != null) {
                UserContext.set(user);
            }
        }
    }

    private void writeJson(HttpServletResponse response, Result<?> result) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(result));
        }
    }
}

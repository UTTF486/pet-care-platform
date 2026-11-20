package com.petcare.config;

import com.petcare.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 相关配置：跨域 + 拦截器 + 密码加密器。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * BCrypt：自带随机盐，同一个密码每次加密结果都不同，比 MD5 安全得多。
     * 这里只引 spring-security-crypto 模块，不引整个 Security 框架，轻量且够用。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 只拦业务接口
                .addPathPatterns("/api/**")
                // 登录注册不需要 Token
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/error");
    }

    /**
     * 开发环境跨域配置。
     * 前端用 Vite 代理时其实走不到这里，但直接访问 8080 或用 Postman 调试时需要。
     * 生产环境务必把 allowedOriginPatterns 收窄成具体域名。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

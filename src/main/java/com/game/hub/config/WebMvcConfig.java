package com.game.hub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final RoleGuardInterceptor roleGuardInterceptor;
    private final PageAccessLogInterceptor pageAccessLogInterceptor;

    public WebMvcConfig(RoleGuardInterceptor roleGuardInterceptor,
                        PageAccessLogInterceptor pageAccessLogInterceptor) {
        this.roleGuardInterceptor = roleGuardInterceptor;
        this.pageAccessLogInterceptor = pageAccessLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pageAccessLogInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/css/**",
                "/js/**",
                "/lib/**",
                "/images/**",
                "/img/**",
                "/uploads/**",
                "/music/**",
                "/webjars/**",
                "/ws/**",
                "/api/connectivity/**",
                "/error",
                "/favicon.ico",
                "/**/*.png",
                "/**/*.jpg",
                "/**/*.jpeg",
                "/**/*.gif",
                "/**/*.svg",
                "/**/*.webp",
                "/**/*.ico",
                "/**/*.map"
            );

        registry.addInterceptor(roleGuardInterceptor)
            .addPathPatterns("/admin/**", "/manager/**", "/notification-admin/**");
    }
}

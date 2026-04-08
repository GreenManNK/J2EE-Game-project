package com.game.hub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final RoleGuardInterceptor roleGuardInterceptor;
    private final PageAccessLogInterceptor pageAccessLogInterceptor;
    private final BannedUserAccessInterceptor bannedUserAccessInterceptor;
    private final String uploadRoot;

    public WebMvcConfig(RoleGuardInterceptor roleGuardInterceptor,
                        PageAccessLogInterceptor pageAccessLogInterceptor,
                        BannedUserAccessInterceptor bannedUserAccessInterceptor,
                        @Value("${app.upload.root:uploads}") String uploadRoot) {
        this.roleGuardInterceptor = roleGuardInterceptor;
        this.pageAccessLogInterceptor = pageAccessLogInterceptor;
        this.bannedUserAccessInterceptor = bannedUserAccessInterceptor;
        this.uploadRoot = uploadRoot;
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

        registry.addInterceptor(bannedUserAccessInterceptor)
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
            .addPathPatterns(
                "/admin/**",
                "/manager/**",
                "/notification-admin/**",
                "/history/export-csv",
                "/history/export-excel",
                "/leaderboard/export-csv",
                "/leaderboard/export-excel",
                "/manager/export-users-csv",
                "/manager/export-users-excel"
            );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String configuredRoot = (uploadRoot == null || uploadRoot.isBlank()) ? "uploads" : uploadRoot.trim();
        String fileUploadLocation = Paths.get(configuredRoot).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(fileUploadLocation, "classpath:/static/uploads/");
    }
}

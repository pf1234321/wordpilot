/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.config;

import com.scriptagent.writing.api.interceptor.TokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册全局 {@link TokenInterceptor}.
 *
 * <p>拦截 /api/** 全部业务请求，放行登录接口 /api/auth/login 与健康检查 /api/health （其余一律鉴权，防登录接口被拦死）.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final StringRedisTemplate redis;

  public WebConfig(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new TokenInterceptor(redis))
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login", "/api/health");
  }
}

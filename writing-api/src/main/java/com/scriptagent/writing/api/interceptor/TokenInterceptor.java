/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.interceptor;

import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.constants.RedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全局鉴权拦截器：校验请求 Header 中的 Token，解析出 userId 写入 {@link UserContext}.
 *
 * <p>无 Token / Token 已失效（Redis 无此 key）→ 401 拦截；请求结束 {@code afterCompletion} 必须 {@link
 * UserContext#clear()}，否则线程复用会把上个请求的 userId 带给下个请求（串号）. 登录接口由 WebConfig 放行，其余 /api/** 一律拦截.
 */
public class TokenInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(TokenInterceptor.class);

  private final StringRedisTemplate redis;

  public TokenInterceptor(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String token = request.getHeader("Authorization");
    if (token == null || token.isBlank()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }
    String userId = redis.opsForValue().get(RedisKeys.token(token.trim()));
    if (userId == null) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }
    try {
      UserContext.set(Long.valueOf(userId));
    } catch (NumberFormatException ex) {
      // Redis 中 token 映射应为数字 userId；脏数据视为失效 Token，拒绝并记录
      log.warn("Token 映射的 userId 非法 token={}", token);
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    UserContext.clear();
  }
}

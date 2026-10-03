/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.interceptor;

import com.scriptagent.writing.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** 鉴权拦截器测试：无/失效 Token 401，有效 Token 写 UserContext，请求结束清空防串号. */
@ExtendWith(MockitoExtension.class)
class TokenInterceptorTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOps;

  private TokenInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new TokenInterceptor(redis);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @DisplayName("无 Token 返回 401")
  @Test
  void noToken_returns401AndNotPass() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean pass = interceptor.preHandle(request, response, new Object());

    assertFalse(pass);
    assertEquals(401, response.getStatus());
    assertNull(UserContext.peek());
  }

  @DisplayName("Token 有效写入 UserContext")
  @Test
  void validToken_writesUserContextAndPass() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("token:abc")).thenReturn("1");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean pass = interceptor.preHandle(request, response, new Object());

    assertTrue(pass);
    assertEquals(1L, UserContext.peek());
  }

  @DisplayName("Token 已失效返回 401")
  @Test
  void expiredToken_returns401AndNotPass() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("token:dead")).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "dead");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean pass = interceptor.preHandle(request, response, new Object());

    assertFalse(pass);
    assertEquals(401, response.getStatus());
    assertNull(UserContext.peek());
  }

  @DisplayName("请求结束上下文被清空，不复用串号")
  @Test
  void userContext_clearedAfterRequest_preventsLeak() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("token:valid")).thenReturn("1");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "valid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertNotNull(UserContext.peek());

    interceptor.afterCompletion(request, response, new Object(), null);
    assertNull(UserContext.peek());
  }
}

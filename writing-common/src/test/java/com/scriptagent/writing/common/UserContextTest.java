/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 用户上下文（ThreadLocal）生命周期测试：登录态注入、清除、防串号. */
class UserContextTest {

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  void set_thenPeek_returnsCurrentUserId() {
    UserContext.set(42L);
    assertEquals(42L, UserContext.peek());
  }

  @Test
  void clear_removesCurrentUser() {
    UserContext.set(42L);
    UserContext.clear();
    assertNull(UserContext.peek());
  }

  @Test
  void require_withoutLogin_throwsIllegalStateException() {
    assertThrows(IllegalStateException.class, UserContext::require);
  }

  @Test
  void require_afterSet_returnsUserId() {
    UserContext.set(7L);
    assertEquals(7L, UserContext.require());
  }

  @DisplayName("请求结束上下文被清空，不复用串号")
  @Test
  void userContext_clearedAfterRequest_preventsLeak() {
    // 模拟一次带登录态的请求：拦截器 preHandle 写入，请求结束 afterCompletion 必须清空
    UserContext.set(1L);
    assertNotNull(UserContext.peek());
    // afterCompletion 动作：清空，否则线程复用会把上个请求的 userId 带给下个请求
    UserContext.clear();
    assertNull(UserContext.peek());
  }
}

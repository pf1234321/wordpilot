/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common;

/**
 * 当前线程登录用户上下文.
 *
 * <p>由 {@code writing-api} 的 Token 拦截器写入，由 Service 层用于查询时强制携带 user_id. 使用 ThreadLocal
 * 隔离请求线程，框架级拦截器负责 {@link #clear()}.
 */
public final class UserContext {

  private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

  private UserContext() {}

  public static void set(Long userId) {
    CURRENT_USER_ID.set(userId);
  }

  /** 取当前用户 id；未登录时抛 {@link IllegalStateException}. */
  public static Long require() {
    Long uid = CURRENT_USER_ID.get();
    if (uid == null) {
      throw new IllegalStateException("未登录或 UserContext 未注入");
    }
    return uid;
  }

  /** 取当前用户 id；未登录时返回 null（用于匿名接口）. */
  public static Long peek() {
    return CURRENT_USER_ID.get();
  }

  public static void clear() {
    CURRENT_USER_ID.remove();
  }
}

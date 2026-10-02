/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.constants;

/** Redis Key 模板，统一管理 key 前缀，防止散落与冲突. */
public final class RedisKeys {

  /** Token → UserId 映射（UUID token，TTL 由配置控制） */
  public static final String TOKEN_PREFIX = "token:";

  /** 用户短期会话：user 输入 + assistant 最终回复，默认最近 10 轮 */
  public static final String SESSION_PREFIX = "session:";

  /** SSE 流式缓存 */
  public static final String SSE_CACHE_PREFIX = "sse:cache:";

  /** 接口限流 */
  public static final String RATE_LIMIT_PREFIX = "ratelimit:";

  private RedisKeys() {}

  public static String token(String token) {
    return TOKEN_PREFIX + token;
  }

  public static String session(long userId, String sessionId) {
    return SESSION_PREFIX + userId + ":" + sessionId;
  }

  public static String sseCache(long userId, String sessionId) {
    return SSE_CACHE_PREFIX + userId + ":" + sessionId;
  }

  public static String rateLimit(long userId, String endpoint) {
    return RATE_LIMIT_PREFIX + userId + ":" + endpoint;
  }
}

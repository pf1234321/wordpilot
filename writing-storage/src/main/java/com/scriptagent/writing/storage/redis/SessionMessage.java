/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.redis;

import java.time.Instant;

/** 单条短期会话消息：{@code role} + {@code content} + {@code timestamp}. 序列化到 Redis 用 Jackson. */
public record SessionMessage(SessionRole role, String content, Instant timestamp) {

  public static SessionMessage of(SessionRole role, String content) {
    return new SessionMessage(role, content, Instant.now());
  }
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.redis;

/**
 * 短期会话角色枚举：仅对话层 {@code user} / {@code assistant} 两类，think / tool 等中间态不属于 本枚举（constitution
 * 原则八：短期记忆只存对话层）.
 */
public enum SessionRole {
  USER("user"),
  ASSISTANT("assistant");

  private final String wireName;

  SessionRole(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }
}

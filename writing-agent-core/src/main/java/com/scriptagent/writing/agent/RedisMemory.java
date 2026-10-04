/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.redis.RedisMemoryStore;
import com.scriptagent.writing.storage.redis.SessionMessage;
import com.scriptagent.writing.storage.redis.SessionRole;
import java.util.List;

/**
 * 短期会话记忆实现（对齐 {@link MemoryBase}），把多轮对话落到 Redis {@code session:{userId}:{sessionId}}.
 *
 * <p>按会话构造（{@code userId}+{@code sessionId} 固定），不持有跨会话状态（宪法原则二：状态外置，Agent 无状态）. 只落对话层
 * role（user/assistant）；think/tool 等中间态**静默丢弃**、不写入 Redis（宪法原则八，课件关键回归 {@code
 * shortTermMemory_storesOnlyDialog_dropsIntermediate} 语义——"丢弃"而非抛错；底层 {@link RedisMemoryStore}
 * 仍保留抛错守卫作纵深防御）. 窗口滑出由 {@link RedisMemoryStore#loadRecent} 承担.
 */
public class RedisMemory implements MemoryBase {

  private final Long userId;
  private final String sessionId;
  private final RedisMemoryStore store;
  private final int maxRounds;

  public RedisMemory(Long userId, String sessionId, RedisMemoryStore store, int maxRounds) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.store = store;
    this.maxRounds = maxRounds;
  }

  @Override
  public void append(String role, String content) {
    SessionRole sessionRole = toSessionRole(role);
    if (sessionRole == null) {
      // 丢弃推理/tool 中间态（宪法原则八），不写入 Redis
      return;
    }
    store.append(userId, sessionId, sessionRole, content);
  }

  @Override
  public List<SessionMessage> loadRecent(int maxRounds) {
    return store.loadRecent(userId, sessionId, maxRounds);
  }

  /** 当前配置的短期窗口轮数（构造时注入，来自 {@code memory.short-term.max-rounds}）. */
  public int maxRounds() {
    return maxRounds;
  }

  /** 仅识别对话层 role；think/tool 等中间态返回 null（由 {@link #append} 丢弃）. */
  private static SessionRole toSessionRole(String role) {
    if ("user".equals(role)) {
      return SessionRole.USER;
    }
    if ("assistant".equals(role)) {
      return SessionRole.ASSISTANT;
    }
    return null;
  }
}

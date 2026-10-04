/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.redis.SessionMessage;
import java.util.List;

/**
 * 短期会话记忆抽象，对齐 AgentScope {@code MemoryBase} 契约（宪法原则三/八）.
 *
 * <p>实现落在项目自有 Redis（{@link RedisMemory} 包装 {@code RedisMemoryStore}），不引入 AgentScope 默认存储.
 * 只承载"对话层消息"（user 输入 + assistant 最终回复）；think/tool 等推理中间态不属于本抽象，由实现方丢弃.
 */
public interface MemoryBase {

  /** 追加一条对话层消息；非对话层 role（think/tool 等中间态）由实现方丢弃，不写入 Redis. */
  void append(String role, String content);

  /** 取最近 {@code maxRounds} 轮对话层消息（按时间序、最早在前）；超过窗口的最早轮次滑出. */
  List<SessionMessage> loadRecent(int maxRounds);
}

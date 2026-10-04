/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import java.util.List;

/**
 * 长期记忆抽象，对齐 AgentScope {@code LongTermMemoryBase} 契约（宪法原则三/八）.
 *
 * <p>实现落在项目自有 MySQL {@code writing_memory}（{@link LongTermMemoryService} 包装 {@code
 * WritingMemoryRepository}），按 user_id 隔离. Agent 经 {@code save_memory} 工具回写稳定偏好，系统不自动抽取.
 */
public interface LongTermMemoryBase {

  /** 写入一条长期记忆（强制携带 user_id，来源 {@code UserContext}）. */
  void save(Long userId, String memoryType, String content, String source);

  /** 按 user_id 加载该用户长期记忆内容（量大按最新截断），用于注入 system prompt. */
  List<String> load(Long userId);
}

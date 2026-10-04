/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.redis.SessionMessage;
import com.scriptagent.writing.storage.redis.SessionRole;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 上下文压缩（AutoContextMemory）：当短期记忆轮数超过阈值时，把更早对话蒸馏/截断，防止撑爆模型上下文（宪法原则八）.
 *
 * <p>AgentScope 官方强调的顺序：**压缩前先把值得长期记住的事实写入长期记忆，再压缩短期记忆**——本实现 {@link #compress} 先抽取最早 user
 * 指令为长期记忆（{@link LongTermMemoryService#save}），再截断保留最近 {@code maxRounds} 轮，保证有价值信息不因压缩丢失.
 * 核心阶段做"简单截断"，LLM 摘要蒸馏为扩展项（TS §5.2）.
 */
@Component
public class AutoContextMemory {

  private final LongTermMemoryService longTermMemoryService;
  private final int maxRounds;

  public AutoContextMemory(
      LongTermMemoryService longTermMemoryService,
      @Value("${agent.context.max-rounds:10}") int maxRounds) {
    this.longTermMemoryService = longTermMemoryService;
    this.maxRounds = maxRounds;
  }

  /** 当前会话轮数是否超过压缩阈值. */
  public boolean shouldCompress(int rounds) {
    return rounds > maxRounds;
  }

  /**
   * 压缩：先抽取最早 user 指令落长期记忆，再截断保留最近 {@code maxRounds} 轮.
   *
   * @return 压缩后保留的短期记忆（最近 maxRounds 轮）
   */
  public List<SessionMessage> compress(Long userId, List<SessionMessage> history) {
    // 压缩前先抽长期记忆（保序：save 先于截断，有价值信息不因压缩丢失）
    for (SessionMessage m : history) {
      if (m.role() == SessionRole.USER && !isBlank(m.content())) {
        longTermMemoryService.save(userId, "habit", m.content(), "auto-compress");
        break; // 简单启发：最早一条用户指令视为长期保持的偏好
      }
    }
    if (history.size() <= maxRounds) {
      return history;
    }
    return new ArrayList<>(history.subList(history.size() - maxRounds, history.size()));
  }

  /** 压缩阈值（轮数），来自 {@code agent.context.max-rounds}. */
  public int maxRounds() {
    return maxRounds;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}

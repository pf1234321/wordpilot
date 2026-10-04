/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.redis.RedisMemoryStore;
import com.scriptagent.writing.storage.redis.SessionRole;
import org.springframework.stereotype.Component;

/**
 * 流式封装 + 会话缓存回写：把生成结果经 {@link StreamOutput} 逐段推送，同时把 user 输入与 assistant 最终回复追加到 Redis
 * 会话（短期记忆，宪法原则八：只存对话层消息、丢弃推理/tool 中间态）.
 */
@Component
public class StreamingResponseHandler {

  private final RedisMemoryStore redisMemoryStore;

  public StreamingResponseHandler(RedisMemoryStore redisMemoryStore) {
    this.redisMemoryStore = redisMemoryStore;
  }

  /**
   * 推送一次推理结果并回写短期会话缓存.
   *
   * @param userMessage 本轮用户输入（写入短期记忆）
   * @param assistantReply 生成的最终回复（写入短期记忆 + 逐段推送）
   */
  public void emit(
      Long userId,
      String sessionId,
      String userMessage,
      String assistantReply,
      StreamOutput output) {
    if (output != null) {
      output.onToken(assistantReply);
      output.onComplete();
    }
    // 只落对话层消息：user 输入 + assistant 最终回复（丢弃推理/tool 中间态）
    redisMemoryStore.append(userId, sessionId, SessionRole.USER, userMessage);
    redisMemoryStore.append(userId, sessionId, SessionRole.ASSISTANT, assistantReply);
  }
}

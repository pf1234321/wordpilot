/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.model.Message;
import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.redis.SessionMessage;
import java.util.List;

/**
 * 一次推理的编排（写作 Agent 的调用入口）：注入三层记忆 → 调模型服务 LLM → 流式输出 → 回写 Redis 会话缓存.
 *
 * <p>按会话构造（携带 mode / {@link RedisMemory} / 可选检索工具），不持有跨会话状态（宪法原则二：状态外置）. LLM 调用统一经 {@link
 * ModelService}（宪法三/决策九，不直连大模型）. 仿写模式先用 {@link ESRetrieveTool} 取参考素材再 组装 Prompt.
 */
public class WritingAgent {

  private final String mode;
  private final PromptManager promptManager;
  private final LongTermMemoryService longTermMemoryService;
  private final RedisMemory redisMemory;
  private final ModelService modelService;
  private final AutoContextMemory autoContextMemory;
  private final StreamingResponseHandler streamingResponseHandler;
  private final ESRetrieveTool esRetrieveTool;

  public WritingAgent(
      String mode,
      PromptManager promptManager,
      LongTermMemoryService longTermMemoryService,
      RedisMemory redisMemory,
      ModelService modelService,
      AutoContextMemory autoContextMemory,
      StreamingResponseHandler streamingResponseHandler,
      ESRetrieveTool esRetrieveTool) {
    this.mode = mode;
    this.promptManager = promptManager;
    this.longTermMemoryService = longTermMemoryService;
    this.redisMemory = redisMemory;
    this.modelService = modelService;
    this.autoContextMemory = autoContextMemory;
    this.streamingResponseHandler = streamingResponseHandler;
    this.esRetrieveTool = esRetrieveTool;
  }

  /**
   * 执行一次对话推理，返回生成的文稿.
   *
   * @param userId 当前用户（短期/长期记忆按 user_id 隔离）
   * @param sessionId 会话 id（Redis 会话缓存 key）
   * @param userMessage 本轮用户输入
   * @param output 流式输出回调（可为 null，仅同步返回）
   */
  public String run(Long userId, String sessionId, String userMessage, StreamOutput output) {
    List<String> longTerm = longTermMemoryService.load(userId);
    List<SessionMessage> history = redisMemory.loadRecent(redisMemory.maxRounds());
    if (autoContextMemory.shouldCompress(history.size())) {
      history = autoContextMemory.compress(userId, history);
    }
    List<String> referenceMaterials = List.of();
    if (esRetrieveTool != null) {
      // SSE 独立线程无 ThreadLocal UserContext，显式传入 userId（来源登录鉴权链路）
      String refs = esRetrieveTool.execute(userId, userMessage);
      if (refs != null && !refs.trim().isEmpty()) {
        referenceMaterials = List.of(refs);
      }
    }
    List<Message> messages =
        promptManager.assembleMessages(mode, longTerm, history, userMessage, referenceMaterials);
    String result = modelService.chat(messages);
    streamingResponseHandler.emit(userId, sessionId, userMessage, result, output);
    return result;
  }

  /** 当前模式（package-private，供工厂测试断言配置）. */
  String mode() {
    return mode;
  }

  /** 当前检索工具（package-private，供工厂测试断言 dialog/template 无工具、rag 附工具）. */
  ESRetrieveTool esRetrieveTool() {
    return esRetrieveTool;
  }
}

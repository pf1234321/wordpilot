/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.redis.RedisMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Agent 工厂：按写作模式（dialog / rag / template）生成无状态写作 Agent 配置，三类共享同一套底座（宪法原则九）.
 *
 * <p>每次请求按需生成新的 {@link RedisMemory}（携带 userId/sessionId）与 {@link WritingAgent}，不持有任何会话状态
 * （宪法原则二：状态外置，会话在 Redis、素材在 ES、业务在 MySQL）. 未知模式抛 {@link IllegalArgumentException}，不 静默降级.
 * LLM/Embedding 统一经 {@link ModelService}（宪法三/决策九）.
 */
@Component
public class AgentFactory {

  private final PromptManager promptManager;
  private final LongTermMemoryService longTermMemoryService;
  private final RedisMemoryStore redisMemoryStore;
  private final ModelService modelService;
  private final AutoContextMemory autoContextMemory;
  private final StreamingResponseHandler streamingResponseHandler;
  private final ESRetrieveTool esRetrieveTool;
  private final int maxRounds;

  public AgentFactory(
      PromptManager promptManager,
      LongTermMemoryService longTermMemoryService,
      RedisMemoryStore redisMemoryStore,
      ModelService modelService,
      AutoContextMemory autoContextMemory,
      StreamingResponseHandler streamingResponseHandler,
      ESRetrieveTool esRetrieveTool,
      @Value("${memory.short-term.max-rounds:10}") int maxRounds) {
    this.promptManager = promptManager;
    this.longTermMemoryService = longTermMemoryService;
    this.redisMemoryStore = redisMemoryStore;
    this.modelService = modelService;
    this.autoContextMemory = autoContextMemory;
    this.streamingResponseHandler = streamingResponseHandler;
    this.esRetrieveTool = esRetrieveTool;
    this.maxRounds = maxRounds;
  }

  /** 按模式生成写作 Agent；未知模式抛 {@link IllegalArgumentException}. */
  public WritingAgent create(String mode, Long userId, String sessionId) {
    RedisMemory redisMemory = new RedisMemory(userId, sessionId, redisMemoryStore, maxRounds);
    switch (mode) {
      case "dialog":
      case "template":
        // 对话/模板：无检索工具（仅 Base + Prompt 差异）；合并分支避免 SpotBugs 重复分支告警
        return new WritingAgent(
            mode,
            promptManager,
            longTermMemoryService,
            redisMemory,
            modelService,
            autoContextMemory,
            streamingResponseHandler,
            null);
      case "rag":
        return new WritingAgent(
            mode,
            promptManager,
            longTermMemoryService,
            redisMemory,
            modelService,
            autoContextMemory,
            streamingResponseHandler,
            esRetrieveTool);
      default:
        throw new IllegalArgumentException("未知写作模式: " + mode);
    }
  }
}

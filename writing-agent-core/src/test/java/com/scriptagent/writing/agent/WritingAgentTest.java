/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.model.Message;
import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.redis.SessionMessage;
import com.scriptagent.writing.storage.redis.SessionRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WritingAgent 编排单测：三模式都能产出、超长触发压缩且压缩前先落长期记忆（课件 harness 关键回归）. */
@ExtendWith(MockitoExtension.class)
class WritingAgentTest {

  private static final Long USER_ID = 1L;
  private static final String SESSION = "s1";

  @Mock private PromptManager promptManager;
  @Mock private LongTermMemoryService longTermMemoryService;
  @Mock private RedisMemory redisMemory;
  @Mock private ModelService modelService;
  @Mock private StreamingResponseHandler streamingResponseHandler;
  @Mock private ESRetrieveTool esRetrieveTool;
  @Mock private StreamOutput output;

  private WritingAgent newAgent(
      String mode, AutoContextMemory autoContextMemory, ESRetrieveTool tool) {
    return new WritingAgent(
        mode,
        promptManager,
        longTermMemoryService,
        redisMemory,
        modelService,
        autoContextMemory,
        streamingResponseHandler,
        tool);
  }

  /** maxRounds=0：任何历史都触发压缩（确定性触发，便于断言"压缩前先落长期记忆"的保序）. */
  private AutoContextMemory alwaysCompress() {
    return new AutoContextMemory(longTermMemoryService, 0);
  }

  private AutoContextMemory neverCompress() {
    return new AutoContextMemory(longTermMemoryService, 100);
  }

  private void stubContext(boolean compress) {
    when(longTermMemoryService.load(USER_ID)).thenReturn(List.of());
    when(redisMemory.maxRounds()).thenReturn(10);
    when(redisMemory.loadRecent(10)).thenReturn(List.of());
  }

  @Test
  @DisplayName("longContext_extractsLongTermBeforeCompress：超长触发压缩，且压缩前先落长期记忆（先抽长期记忆再压缩）")
  void longContext_extractsLongTermBeforeCompress() {
    List<SessionMessage> history =
        List.of(
            new SessionMessage(SessionRole.USER, "以后都写 800 字", Instant.now()),
            new SessionMessage(SessionRole.ASSISTANT, "好的", Instant.now()));
    when(longTermMemoryService.load(USER_ID)).thenReturn(List.of());
    when(redisMemory.maxRounds()).thenReturn(10);
    when(redisMemory.loadRecent(10)).thenReturn(history);
    when(promptManager.assembleMessages(eq("dialog"), any(), any(), eq("msg"), any()))
        .thenReturn(List.of(new Message("system", "s"), new Message("user", "msg")));
    when(modelService.chat(any())).thenReturn("初稿如下…");

    WritingAgent agent = newAgent("dialog", alwaysCompress(), null);
    String result = agent.run(USER_ID, SESSION, "msg", output);

    assertEquals("初稿如下…", result);
    // 压缩前先落长期记忆（保序：save 先于截断，有价值信息不因压缩丢失）
    verify(longTermMemoryService)
        .save(eq(USER_ID), eq("habit"), eq("以后都写 800 字"), eq("auto-compress"));
    verify(streamingResponseHandler)
        .emit(eq(USER_ID), eq(SESSION), eq("msg"), eq("初稿如下…"), eq(output));
  }

  @Test
  @DisplayName("dialog 模式：经模型服务产出，不触发检索工具")
  void run_dialog_producesWithoutRetrieval() {
    stubContext(false);
    when(promptManager.assembleMessages(eq("dialog"), any(), any(), eq("写推文"), any()))
        .thenReturn(List.of(new Message("system", "s"), new Message("user", "写推文")));
    when(modelService.chat(any())).thenReturn("推文正文");

    WritingAgent agent = newAgent("dialog", neverCompress(), null);
    assertEquals("推文正文", agent.run(USER_ID, SESSION, "写推文", output));
    verify(esRetrieveTool, never()).execute(eq(USER_ID), anyString());
  }

  @Test
  @DisplayName("rag 模式：先经 ESRetrieveTool 取参考素材，再经模型服务产出")
  void run_rag_retrievesReferenceThenProduces() {
    stubContext(false);
    when(esRetrieveTool.execute(USER_ID, "按素材仿写")).thenReturn("参考切片一\n参考切片二");
    when(promptManager.assembleMessages(eq("rag"), any(), any(), eq("按素材仿写"), any()))
        .thenReturn(List.of(new Message("system", "s"), new Message("user", "按素材仿写")));
    when(modelService.chat(any())).thenReturn("仿写正文");

    WritingAgent agent = newAgent("rag", neverCompress(), esRetrieveTool);
    assertEquals("仿写正文", agent.run(USER_ID, SESSION, "按素材仿写", output));
    verify(esRetrieveTool).execute(USER_ID, "按素材仿写");
  }

  @Test
  @DisplayName("template 模式：经模型服务产出，不触发检索工具")
  void run_template_produces() {
    stubContext(false);
    when(promptManager.assembleMessages(eq("template"), any(), any(), eq("填参数"), any()))
        .thenReturn(List.of(new Message("system", "s"), new Message("user", "填参数")));
    when(modelService.chat(any())).thenReturn("模板正文");

    WritingAgent agent = newAgent("template", neverCompress(), null);
    assertEquals("模板正文", agent.run(USER_ID, SESSION, "填参数", output));
    verify(esRetrieveTool, never()).execute(anyString());
  }
}

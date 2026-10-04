/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.redis.RedisMemoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Agent 工厂单测：三模式生成正确配置、未知模式抛错（课件 harness）. */
@ExtendWith(MockitoExtension.class)
class AgentFactoryTest {

  private static final Long USER_ID = 1L;
  private static final String SESSION = "s1";

  @Mock private PromptManager promptManager;
  @Mock private LongTermMemoryService longTermMemoryService;
  @Mock private RedisMemoryStore redisMemoryStore;
  @Mock private ModelService modelService;
  @Mock private AutoContextMemory autoContextMemory;
  @Mock private StreamingResponseHandler streamingResponseHandler;
  @Mock private ESRetrieveTool esRetrieveTool;

  private AgentFactory newFactory() {
    return new AgentFactory(
        promptManager,
        longTermMemoryService,
        redisMemoryStore,
        modelService,
        autoContextMemory,
        streamingResponseHandler,
        esRetrieveTool,
        10);
  }

  @Test
  @DisplayName("dialog 模式：生成对话 Agent，无检索工具")
  void create_dialog_buildsAgentWithoutRetrieveTool() {
    WritingAgent agent = newFactory().create("dialog", USER_ID, SESSION);
    assertEquals("dialog", agent.mode());
    assertNull(agent.esRetrieveTool());
  }

  @Test
  @DisplayName("rag 模式：生成仿写 Agent，附 ESRetrieveTool")
  void create_rag_buildsAgentWithRetrieveTool() {
    WritingAgent agent = newFactory().create("rag", USER_ID, SESSION);
    assertEquals("rag", agent.mode());
    assertSame(esRetrieveTool, agent.esRetrieveTool());
  }

  @Test
  @DisplayName("template 模式：生成模板 Agent，无检索工具")
  void create_template_buildsAgentWithoutRetrieveTool() {
    WritingAgent agent = newFactory().create("template", USER_ID, SESSION);
    assertEquals("template", agent.mode());
    assertNull(agent.esRetrieveTool());
  }

  @Test
  @DisplayName("未知模式抛 IllegalArgumentException，不静默降级")
  void create_unknownMode_throws() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newFactory().create("foo", USER_ID, SESSION));
    assertTrue(ex.getMessage().contains("未知写作模式"));
    verify(esRetrieveTool, never()).execute(org.mockito.ArgumentMatchers.anyString());
  }
}

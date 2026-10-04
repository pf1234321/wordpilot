/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.model.Message;
import com.scriptagent.writing.storage.redis.SessionMessage;
import com.scriptagent.writing.storage.redis.SessionRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prompt 管理单测：核心记忆排最前、三类 Prompt 隔离、长期记忆注入（课件 harness）. */
class PromptManagerTest {

  private final PromptManager promptManager = new PromptManager();

  @Test
  @DisplayName("核心记忆固定注入最前（排在最前、不被长期记忆/参考素材挤占）")
  void coreMemory_isAlwaysFirst() {
    String sys = promptManager.buildSystemPrompt("dialog", List.of("喜欢简洁风格"));
    assertTrue(sys.startsWith(promptManager.corePrompt("dialog")));
  }

  @Test
  @DisplayName("三类 Prompt 骨架隔离：dialog/rag/template 的 corePrompt 互不相同")
  void threeModes_haveIsolatedSkeletons() {
    String dialog = promptManager.corePrompt("dialog");
    String rag = promptManager.corePrompt("rag");
    String template = promptManager.corePrompt("template");
    assertNotEquals(dialog, rag);
    assertNotEquals(rag, template);
    assertNotEquals(dialog, template);
  }

  @Test
  @DisplayName("长期记忆按序注入（核心之后），非空时含长期记忆块")
  void longTerm_isInjectedWhenPresent() {
    String sys = promptManager.buildSystemPrompt("dialog", List.of("喜欢简洁风格"));
    assertTrue(sys.contains("喜欢简洁风格"));
    assertTrue(sys.contains("【长期记忆（用户偏好）】"));
  }

  @Test
  void emptyLongTerm_injectsNoBlock() {
    String sys = promptManager.buildSystemPrompt("dialog", List.of());
    assertFalse(sys.contains("【长期记忆（用户偏好）】"));
  }

  @Test
  @DisplayName("仿写模式注入参考素材（其余模式不注入）")
  void referenceMaterials_injectedForRag() {
    String sys = promptManager.buildSystemPrompt("rag", List.of(), List.of("参考切片"));
    assertTrue(sys.contains("参考切片"));
    assertTrue(sys.contains("【参考素材】"));
  }

  @Test
  @DisplayName("assembleMessages：system(核心+长期) → 对话历史 → 当前用户输入")
  void assembleMessages_startsWithSystemThenHistoryThenUser() {
    List<SessionMessage> history =
        List.of(
            new SessionMessage(SessionRole.USER, "上一轮问题", Instant.now()),
            new SessionMessage(SessionRole.ASSISTANT, "上一轮回答", Instant.now()));
    List<Message> messages =
        promptManager.assembleMessages("dialog", List.of(), history, "本轮输入", List.of());
    assertEquals("system", messages.get(0).role());
    assertEquals("user", messages.get(1).role());
    assertEquals("上一轮问题", messages.get(1).content());
    assertEquals("assistant", messages.get(2).role());
    assertEquals("user", messages.get(3).role());
    assertEquals("本轮输入", messages.get(3).content());
  }

  @Test
  void corePrompt_unknownModeThrows() {
    assertThrows(IllegalArgumentException.class, () -> promptManager.corePrompt("foo"));
  }
}

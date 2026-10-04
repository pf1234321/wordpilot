/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.model.Message;
import com.scriptagent.writing.storage.redis.SessionMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Prompt 管理：三类写作场景 Prompt 隔离与组装，按"核心记忆 → 长期记忆 → 短期记忆"顺序注入（宪法原则八/九）.
 *
 * <p>核心记忆（Agent 身份 / 任务定义 / Prompt 骨架）固定注入 system prompt 最前、不被压缩清除；长期记忆在对话开始 注入；短期记忆（对话历史）以消息追加.
 * 仿写模式把 {@link ESRetrieveTool} 召回参考素材拼入 system prompt 的 "参考素材"段. Prompt 骨架以 Java
 * 常量内联（不新增资源目录），三类互不相同（宪法原则九：Prompt 隔离）.
 */
@Component
public class PromptManager {

  private static final String DIALOG_CORE =
      "你是 ScriptAgent 的对话写作助手。你负责根据用户的多轮指令，逐步产出流畅、贴合要求的文稿。"
          + "写作需遵循：先理解用户意图，再组织结构，语言准确、段落清晰，篇幅与风格随用户要求调整。";

  private static final String RAG_CORE =
      "你是 ScriptAgent 的素材仿写助手。你会参考用户提供的素材片段，按用户需求仿写/改写出贴合原文风格的文稿。"
          + "写作需遵循：忠实参考素材的事实与表达，不虚构素材未提及的内容，在参考基础上完成需求要求的创作。";

  private static final String TEMPLATE_CORE =
      "你是 ScriptAgent 的模板写作助手。你会依据固定模板与用户填写的参数，按模板结构产出文稿。"
          + "写作需遵循：严格按模板占位与参数渲染，保留模板的既有结构与措辞，仅替换变量位。";

  /** 组装该模式的 system prompt：核心记忆固定最前，其后按需接长期记忆块. */
  public String buildSystemPrompt(String mode, List<String> longTerm) {
    return buildSystemPrompt(mode, longTerm, List.of());
  }

  /**
   * 组装该模式的 system prompt.
   *
   * @param referenceMaterials 仿写模式注入参考素材（其余模式忽略）
   */
  public String buildSystemPrompt(
      String mode, List<String> longTerm, List<String> referenceMaterials) {
    StringBuilder sb = new StringBuilder(corePrompt(mode));
    if (referenceMaterials != null && !referenceMaterials.isEmpty()) {
      sb.append("\n\n【参考素材】\n").append(String.join("\n---\n", referenceMaterials));
    }
    if (longTerm != null && !longTerm.isEmpty()) {
      sb.append("\n\n【长期记忆（用户偏好）】\n").append(String.join("\n", longTerm));
    }
    return sb.toString();
  }

  /** 组装完整对话消息列表：system(核心+长期+参考素材) + 对话历史 + 当前用户输入. */
  public List<Message> assembleMessages(
      String mode,
      List<String> longTerm,
      List<SessionMessage> history,
      String userInput,
      List<String> referenceMaterials) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message("system", buildSystemPrompt(mode, longTerm, referenceMaterials)));
    if (history != null) {
      for (SessionMessage m : history) {
        messages.add(new Message(m.role().wireName(), m.content()));
      }
    }
    messages.add(new Message("user", userInput));
    return messages;
  }

  /** 该模式的核心记忆（身份 + 任务定义）；未知模式抛 {@link IllegalArgumentException}. */
  public String corePrompt(String mode) {
    switch (mode) {
      case "dialog":
        return DIALOG_CORE;
      case "rag":
        return RAG_CORE;
      case "template":
        return TEMPLATE_CORE;
      default:
        throw new IllegalArgumentException("未知写作模式: " + mode);
    }
  }
}

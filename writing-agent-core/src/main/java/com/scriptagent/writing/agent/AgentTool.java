/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

/**
 * 自定义工具抽象，对齐 AgentScope 工具调用契约（宪法原则三）.
 *
 * <p>Agent 侧检索/回写等能力以工具形式暴露；实现落在项目自有 ES/MySQL（{@link ESRetrieveTool} / {@link SaveMemoryTool}），不引入
 * AgentScope 默认实现. 未来新工具（模板检索、文档预览）走同一套抽象.
 */
public interface AgentTool {

  /** 工具名（如 {@code es_retrieve} / {@code save_memory}）. */
  String name();

  /** 执行工具并返回文本结果. */
  String execute(String query);
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.common.UserContext;
import org.springframework.stereotype.Component;

/**
 * {@code save_memory} 内置工具：对话中识别到稳定偏好时回写长期记忆（宪法原则八）.
 *
 * <p>Agent 不自作主张猜、系统不自动抽取——只有显式经本工具回写才落长期记忆. user_id 来源 {@link UserContext#require()}（H4
 * 不变量①），不从前端参数取.
 */
@Component
public class SaveMemoryTool implements AgentTool {

  private final LongTermMemoryService longTermMemoryService;

  public SaveMemoryTool(LongTermMemoryService longTermMemoryService) {
    this.longTermMemoryService = longTermMemoryService;
  }

  @Override
  public String name() {
    return "save_memory";
  }

  @Override
  public String execute(String query) {
    Long userId = UserContext.require();
    longTermMemoryService.save(userId, "other", query, "save_memory");
    return "已保存到长期记忆";
  }
}

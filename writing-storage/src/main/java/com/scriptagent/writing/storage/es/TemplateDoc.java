/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import java.util.ArrayList;
import java.util.List;

/**
 * ES {@code writing_template} 索引的模板文档模型.
 *
 * <p>一条模板 = 一个 JSON 文档，三要素（§7.1）：{@code structure} 格式骨架 + {@code variables} 变量配置 （驱动前端动态表单）+ {@code
 * prompt} 可选写作规范。{@code status} 启用/停用（{@code enabled}/{@code disabled}），停用后不可用于新写作（历史稿件不受影响）。{@code
 * user_id} 按用户隔离，任何访问必须携带.
 */
public class TemplateDoc {

  /** 模板 id（doc id，无则入库时由仓库生成 {@code tpl-} 前缀） */
  private String id;

  /** 归属用户（按用户隔离，禁止越权访问） */
  private Long userId;

  /** 模板名称（如 "周报模板"） */
  private String name;

  /** 模板用途说明 */
  private String description;

  /** 模板正文结构：静态文本 + {@code ${变量名}} 占位符（给 LLM 的格式骨架） */
  private String structure;

  /** 变量配置数组，驱动前端动态表单 */
  private List<TemplateVariable> variables = new ArrayList<>();

  /** 可选额外写作要求 / 规范说明（注入 Prompt） */
  private String prompt;

  /** 启用 / 停用（enabled / disabled，课件字面量） */
  private String status = "enabled";

  /** 创建时间（epoch millis） */
  private Long createTime;

  /** 更新时间（epoch millis） */
  private Long updateTime;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStructure() {
    return structure;
  }

  public void setStructure(String structure) {
    this.structure = structure;
  }

  public List<TemplateVariable> getVariables() {
    return variables;
  }

  public void setVariables(List<TemplateVariable> variables) {
    this.variables = variables == null ? new ArrayList<>() : variables;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Long createTime) {
    this.createTime = createTime;
  }

  public Long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(Long updateTime) {
    this.updateTime = updateTime;
  }
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板变量配置（ES {@code writing_template} 索引 variables 数组元素）.
 *
 * <p>字段与 {@code docs/TechnicalSolution.md §7.1} 逐字对应：key/label/type/required/placeholder/options.
 * {@code variables} 驱动前端动态表单——后端不写死表单结构，模板怎么配表单就怎么长（§7.3 关键设计点）.
 */
public class TemplateVariable {

  /** 变量 key，与 {@code structure} 中 {@code ${key}} 占位符一一对应 */
  private String key;

  /** 表单文案（如 "周报标题"） */
  private String label;

  /** 控件类型：text / textarea / select / number */
  private String type;

  /** 是否必填（渲染前校验，防生成残缺稿） */
  private Boolean required;

  /** 输入占位提示 */
  private String placeholder;

  /** select 类型的候选选项（其他类型为空） */
  private List<String> options = new ArrayList<>();

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean isRequired() {
    return required;
  }

  public void setRequired(Boolean required) {
    this.required = required;
  }

  public String getPlaceholder() {
    return placeholder;
  }

  public void setPlaceholder(String placeholder) {
    this.placeholder = placeholder;
  }

  public List<String> getOptions() {
    return options;
  }

  public void setOptions(List<String> options) {
    this.options = options == null ? new ArrayList<>() : options;
  }
}

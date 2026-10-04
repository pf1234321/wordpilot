/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import com.scriptagent.writing.storage.es.TemplateVariable;
import java.util.List;

/**
 * 模板创建/更新请求体（前端模板管理）.
 *
 * <p>字段与 ES {@code writing_template} 索引一一对应；创建时 {@code name}/{@code structure} 必填，
 * 更新（PATCH）时可只传需变更字段（null 表示不更新）。{@code status} 缺省为 {@code enabled}.
 */
public record TemplateUpsertRequest(
    String name,
    String description,
    String structure,
    String prompt,
    String status,
    List<TemplateVariable> variables) {}

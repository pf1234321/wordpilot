/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 模板写作请求体（第 8 节写作编排）.
 *
 * @param templateId 模板 id（必填，读 ES 模板库并校验归属与启停）
 * @param params 用户填写的模板参数（key 与模板 variables 的 key 一一对应，必填项由渲染校验）
 * @param sessionId 会话 id（可空，缺失由服务端生成）
 */
public record TemplateRequest(
    @NotBlank(message = "模板 id 不能为空") String templateId,
    @NotNull(message = "模板参数不能为空") Map<String, Object> params,
    String sessionId) {}

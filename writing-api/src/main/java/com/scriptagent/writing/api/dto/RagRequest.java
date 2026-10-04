/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 素材仿写（RAG）请求体（第 8 节写作编排）.
 *
 * @param requirement 仿写需求说明（必填）
 * @param sessionId 会话 id（可空，缺失由服务端生成）
 */
public record RagRequest(@NotBlank(message = "仿写需求不能为空") String requirement, String sessionId) {}

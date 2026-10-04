/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 一句话多轮对话写作请求体（第 8 节写作编排）.
 *
 * @param content 本轮用户输入（必填）
 * @param sessionId 会话 id（多轮由前端回传续上下文；可空，缺失由服务端生成）
 */
public record DialogRequest(@NotBlank(message = "写作内容不能为空") String content, String sessionId) {}

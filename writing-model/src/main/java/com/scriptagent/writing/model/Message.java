/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

/**
 * 单条对话消息（DashScope OpenAI 兼容 role/content 结构）.
 *
 * <p>{@code role} 取值 {@code system} / {@code user} / {@code assistant}；{@code content} 为纯文本.
 */
public record Message(String role, String content) {}

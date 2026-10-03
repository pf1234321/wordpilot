/*
 * Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0.
 */

/**
 * Redis 访问层：短期会话记忆（{@code session:{userId}:{sessionId}}）、Token 缓存、SSE 缓存、 限流计数器.
 *
 * <p>短期会话只接受 {@code user} / {@code assistant} 对话层消息（constitution 原则八）； Agent 推理的 think / tool
 * 等中间态一律不写入 Redis. Key 模板统一引用 {@link com.scriptagent.writing.common.constants.RedisKeys}，禁止散落字符串拼接.
 */
package com.scriptagent.writing.storage.redis;

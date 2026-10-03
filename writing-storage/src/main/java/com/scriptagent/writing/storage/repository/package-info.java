/*
 * Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0.
 */

/**
 * Spring Data JPA Repository 接口层：4 张表的用户维度数据访问.
 *
 * <p>所有业务查询 MUST 强制携带 {@code userId}（来源于登录拦截器写入的 {@link
 * com.scriptagent.writing.common.UserContext}，禁止从请求参数取）， 是 constitution 决策四、原则五的硬约束——跨用户数据隔离是 P0
 * 安全事件.
 */
package com.scriptagent.writing.storage.repository;

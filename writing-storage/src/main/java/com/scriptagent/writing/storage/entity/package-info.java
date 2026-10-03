/*
 * Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0.
 */

/**
 * JPA 实体层：4 张核心表（{@code sys_user} / {@code writing_material} / {@code writing_article} / {@code
 * writing_memory}）的实体类.
 *
 * <p>所有实体继承 {@link com.scriptagent.writing.common.audit.Auditable} 并配合 {@link
 * com.scriptagent.writing.common.audit.AuditEntityListener}， 由 Hibernate 监听器在 persist / update
 * 前自动填充 {@code createTime} / {@code updateTime}； 逻辑删除由基类的 {@code @SQLRestriction} + 各实体的
 * {@code @SQLDelete} 共同实现 （物理行不丢，{@code find*} 系列查询自动过滤 {@code deleted=1}）.
 */
package com.scriptagent.writing.storage.entity;

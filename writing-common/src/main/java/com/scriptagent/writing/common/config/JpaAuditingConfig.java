/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 启用 JPA 审计.
 *
 * <p>审计日期字段（{@code @CreatedDate} / {@code @LastModifiedDate}）需要本注解开启， 自定义 {@link
 * com.scriptagent.writing.common.audit.AuditEntityListener} 互不影响.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}

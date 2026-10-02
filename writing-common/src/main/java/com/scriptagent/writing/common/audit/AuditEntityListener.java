/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.audit;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

/**
 * JPA 审计字段自动填充：{@code createTime} / {@code updateTime}.
 *
 * <p>实体类加 {@code @EntityListeners(AuditEntityListener.class)} 即可生效.
 */
public class AuditEntityListener {

  @PrePersist
  public void onCreate(Object entity) {
    LocalDateTime now = LocalDateTime.now();
    if (entity instanceof Auditable auditable) {
      auditable.setCreateTime(now);
      auditable.setUpdateTime(now);
    }
  }

  @PreUpdate
  public void onUpdate(Object entity) {
    if (entity instanceof Auditable auditable) {
      auditable.setUpdateTime(LocalDateTime.now());
    }
  }
}

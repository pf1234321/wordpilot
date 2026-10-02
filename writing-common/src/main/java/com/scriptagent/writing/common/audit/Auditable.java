/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.audit;

import java.time.LocalDateTime;

/**
 * 审计字段契约：{@code createTime} / {@code updateTime}.
 *
 * <p>实体类实现该接口并加 {@code @EntityListeners(AuditEntityListener.class)}， 由监听器在 persist/update 前自动填充时间戳.
 */
public interface Auditable {

  LocalDateTime getCreateTime();

  void setCreateTime(LocalDateTime createTime);

  LocalDateTime getUpdateTime();

  void setUpdateTime(LocalDateTime updateTime);
}

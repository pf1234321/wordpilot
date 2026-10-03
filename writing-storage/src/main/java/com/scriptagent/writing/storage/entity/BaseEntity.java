/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.entity;

import com.scriptagent.writing.common.audit.AuditEntityListener;
import com.scriptagent.writing.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.hibernate.annotations.SQLRestriction;

/**
 * 公共实体基类：主键、审计时间、逻辑删除标记.
 *
 * <p>所有实体继承本类，统一提供 {@code id}/{@code createTime}/{@code updateTime}/{@code deleted}. 时间戳由 {@link
 * AuditEntityListener} 在 persist/update 前自动填充；{@code @SQLRestriction} 让所有查询自动附加 {@code deleted =
 * 0}，配合各实体上的 {@code @SQLDelete} 实现逻辑删除.
 */
@MappedSuperclass
@EntityListeners(AuditEntityListener.class)
@SQLRestriction("deleted = 0")
public abstract class BaseEntity implements Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "create_time", nullable = false, updatable = false, columnDefinition = "datetime")
  private LocalDateTime createTime;

  @Column(name = "update_time", columnDefinition = "datetime")
  private LocalDateTime updateTime;

  @Column(name = "deleted", nullable = false)
  private Integer deleted = 0;

  @Override
  public LocalDateTime getCreateTime() {
    return createTime;
  }

  @Override
  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  @Override
  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  @Override
  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Integer getDeleted() {
    return deleted;
  }

  public void setDeleted(Integer deleted) {
    this.deleted = deleted;
  }
}

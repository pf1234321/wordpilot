/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;

/** 长期记忆实体，映射 {@code writing_memory} 表. */
@Entity
@Table(
    name = "writing_memory",
    indexes = {
      @Index(name = "idx_user_id", columnList = "user_id"),
      @Index(name = "idx_user_deleted", columnList = "user_id,deleted")
    })
@SQLDelete(sql = "UPDATE writing_memory SET deleted = 1 WHERE id = ?")
public class WritingMemory extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "memory_type", length = 20)
  private String memoryType;

  @Column(name = "content", columnDefinition = "text")
  private String content;

  @Column(name = "source", length = 50)
  private String source;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getMemoryType() {
    return memoryType;
  }

  public void setMemoryType(String memoryType) {
    this.memoryType = memoryType;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}

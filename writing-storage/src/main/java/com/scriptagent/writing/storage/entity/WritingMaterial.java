/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;

/** 素材主实体，映射 {@code writing_material} 表. */
@Entity
@Table(
    name = "writing_material",
    indexes = {
      @Index(name = "idx_user_id", columnList = "user_id"),
      @Index(name = "idx_user_deleted", columnList = "user_id,deleted")
    })
@SQLDelete(sql = "UPDATE writing_material SET deleted = 1 WHERE id = ?")
public class WritingMaterial extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "file_name", length = 255)
  private String fileName;

  @Column(name = "file_type", length = 20)
  private String fileType;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "file_path", length = 500)
  private String filePath;

  @Column(name = "content_text", columnDefinition = "longtext")
  private String contentText;

  @Column(name = "chunk_count", nullable = false)
  private Integer chunkCount = 0;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getContentText() {
    return contentText;
  }

  public void setContentText(String contentText) {
    this.contentText = contentText;
  }

  public Integer getChunkCount() {
    return chunkCount;
  }

  public void setChunkCount(Integer chunkCount) {
    this.chunkCount = chunkCount;
  }
}

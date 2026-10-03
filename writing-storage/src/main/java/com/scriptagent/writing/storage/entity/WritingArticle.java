/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;

/** 稿件历史实体，映射 {@code writing_article} 表. */
@Entity
@Table(
    name = "writing_article",
    indexes = {
      @Index(name = "idx_user_id", columnList = "user_id"),
      @Index(name = "idx_write_type", columnList = "write_type"),
      @Index(name = "idx_user_deleted", columnList = "user_id,deleted")
    })
@SQLDelete(sql = "UPDATE writing_article SET deleted = 1 WHERE id = ?")
public class WritingArticle extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "article_title", length = 255)
  private String articleTitle;

  @Column(name = "article_content", columnDefinition = "longtext")
  private String articleContent;

  @Column(name = "write_type", length = 20)
  private String writeType;

  @Column(name = "material_id")
  private Long materialId;

  @Column(name = "template_id")
  private Long templateId;

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getArticleTitle() {
    return articleTitle;
  }

  public void setArticleTitle(String articleTitle) {
    this.articleTitle = articleTitle;
  }

  public String getArticleContent() {
    return articleContent;
  }

  public void setArticleContent(String articleContent) {
    this.articleContent = articleContent;
  }

  public String getWriteType() {
    return writeType;
  }

  public void setWriteType(String writeType) {
    this.writeType = writeType;
  }

  public Long getMaterialId() {
    return materialId;
  }

  public void setMaterialId(Long materialId) {
    this.materialId = materialId;
  }

  public Long getTemplateId() {
    return templateId;
  }

  public void setTemplateId(Long templateId) {
    this.templateId = templateId;
  }
}

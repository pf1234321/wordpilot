/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import com.scriptagent.writing.storage.entity.WritingArticle;
import java.time.LocalDateTime;

/**
 * 稿件列表项视图（第 8 节写作编排，writing-api）.
 *
 * <p>列表页不携带正文（正文交给详情/重新编辑的 {@link ReeditResponse}），避免把持久化实体直接暴露给客户端 （SpotBugs ENTITY_LEAK
 * 门禁；宪法：不向客户端泄漏持久化属性）。
 */
public record ArticleListItem(
    Long id,
    String title,
    String writeType,
    Long materialId,
    Long templateId,
    LocalDateTime createTime) {

  /** 从持久化实体映射为列表视图（只取列表所需字段）. */
  public static ArticleListItem from(WritingArticle a) {
    return new ArticleListItem(
        a.getId(),
        a.getArticleTitle(),
        a.getWriteType(),
        a.getMaterialId(),
        a.getTemplateId(),
        a.getCreateTime());
  }
}

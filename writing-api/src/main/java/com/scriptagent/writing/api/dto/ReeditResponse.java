/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import com.scriptagent.writing.storage.entity.WritingArticle;
import java.time.LocalDateTime;

/**
 * 稿件重新编辑响应：返回历史稿件作为"继续迭代"的上下文（第 8 节稿件历史）.
 *
 * <p>重新编辑语义 = 把历史稿件内容交给前端作为继续写作的上下文，而非原地覆写；内容由 {@link WritingArticle} 映射，不泄露 deleted 等内部字段.
 *
 * @param id 稿件 id
 * @param articleTitle 稿件标题
 * @param articleContent 稿件正文（重新编辑上下文）
 * @param writeType 写作文式（dialog / rag / template）
 * @param materialId 关联素材 id（可空）
 * @param templateId 关联模板 id（可空）
 * @param createTime 创建时间
 */
public record ReeditResponse(
    Long id,
    String articleTitle,
    String articleContent,
    String writeType,
    Long materialId,
    Long templateId,
    LocalDateTime createTime) {

  /** 由稿件实体映射为响应（剥离 deleted 等内部字段）. */
  public static ReeditResponse from(WritingArticle a) {
    return new ReeditResponse(
        a.getId(),
        a.getArticleTitle(),
        a.getArticleContent(),
        a.getWriteType(),
        a.getMaterialId(),
        a.getTemplateId(),
        a.getCreateTime());
  }
}

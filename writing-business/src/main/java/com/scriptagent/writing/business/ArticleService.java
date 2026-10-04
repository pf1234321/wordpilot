/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.entity.WritingArticle;
import com.scriptagent.writing.storage.repository.WritingArticleRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 稿件历史业务（第 8 节写作编排，writing-business）.
 *
 * <p>三类写作统一落稿入口 + 分页查询 + 重新编辑上下文 + 导出 Word。所有方法强制携带 userId（H4 不变量①，来源 {@code
 * UserContext}，由编排/Controller 传入）；详情/重新编辑/导出/删除 MUST 同时校验 id + user_id（FR-011，越权按 不存在处理）。写作文式由 save
 * 校验（dialog/rag/template），拒绝非法值（FR-002 不静默降级）。
 */
@Service
public class ArticleService {

  private static final Set<String> WRITE_TYPES = Set.of("dialog", "rag", "template");

  private final WritingArticleRepository articleRepository;

  public ArticleService(WritingArticleRepository articleRepository) {
    this.articleRepository = articleRepository;
  }

  /**
   * 三类写作统一落稿入口：带 user_id + write_type（FR-005 关键回归）.
   *
   * @param writeType dialog / rag / template（非法值拒绝）
   * @return 稿件 id
   */
  @Transactional
  public Long save(
      Long userId,
      String writeType,
      String title,
      String content,
      Long materialId,
      Long templateId) {
    if (writeType == null || !WRITE_TYPES.contains(writeType)) {
      throw new BusinessException(ErrorCode.UNKNOWN_WRITE_MODE);
    }
    WritingArticle article = new WritingArticle();
    article.setUserId(userId);
    article.setArticleTitle(title);
    article.setArticleContent(content);
    article.setWriteType(writeType);
    article.setMaterialId(materialId);
    article.setTemplateId(templateId);
    return articleRepository.save(article).getId();
  }

  /** 分页查询当前用户稿件，create_time 倒序（FR-006，仅当前用户）. */
  public Page<WritingArticle> pageByUser(Long userId, Pageable pageable) {
    return articleRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
  }

  /** 取一条稿件作为"重新编辑"的上下文（FR-010/FR-011）：历史稿件作为继续迭代的上下文；越权（非本人 id）一律按不存在报错. */
  public WritingArticle getForReedit(Long userId, Long id) {
    return articleRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
  }

  /** 导出 Word：POI XWPFDocument 生成 docx（标题段 + 正文段），返回字节数组（FR-012）;越权同 {@link #getForReedit}. */
  public byte[] exportWord(Long userId, Long id) {
    WritingArticle article = getForReedit(userId, id);
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      doc.createParagraph().createRun().setText(article.getArticleTitle());
      doc.createParagraph().createRun().setText(article.getArticleContent());
      doc.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.EXPORT_FAILED, e.getMessage());
    }
  }

  /** 删除稿件（逻辑删除 {@code @SQLDelete}）；越权同 {@link #getForReedit}. */
  @Transactional
  public void delete(Long userId, Long id) {
    WritingArticle article = getForReedit(userId, id);
    articleRepository.delete(article);
  }
}

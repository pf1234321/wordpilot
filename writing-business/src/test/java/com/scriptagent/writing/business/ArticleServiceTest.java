/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.entity.WritingArticle;
import com.scriptagent.writing.storage.repository.WritingArticleRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 稿件历史验收（第 8 节课件 harness）：统一落稿带 user_id+write_type；分页仅当前用户；重新编辑上下文；导出 Word；越权拒绝. */
class ArticleServiceTest {

  private static final long USER_A = 1L;
  private static final long USER_B = 2L;

  private final WritingArticleRepository articleRepository = mock(WritingArticleRepository.class);
  private final ArticleService service = new ArticleService(articleRepository);

  @Test
  @DisplayName("关键回归：统一落稿带 user_id + write_type（三类写作统一入口）")
  void save_carriesUserIdAndWriteType() {
    when(articleRepository.save(any(WritingArticle.class)))
        .thenAnswer(
            inv -> {
              WritingArticle a = inv.getArgument(0);
              a.setId(100L);
              return a;
            });

    Long id = service.save(USER_A, "rag", "标题", "正文", 5L, null);

    assertEquals(100L, id);
    // 落稿实体带 user_id + write_type
    org.mockito.ArgumentCaptor<WritingArticle> captor =
        org.mockito.ArgumentCaptor.forClass(WritingArticle.class);
    verify(articleRepository).save(captor.capture());
    assertEquals(USER_A, captor.getValue().getUserId());
    assertEquals("rag", captor.getValue().getWriteType());
    assertEquals(5L, captor.getValue().getMaterialId().longValue());
  }

  @Test
  @DisplayName("非法写作文式拒绝落稿（不静默降级）")
  void save_invalidWriteType_rejects() {
    BusinessException e =
        assertThrows(
            BusinessException.class, () -> service.save(USER_A, "ghost", "t", "c", null, null));
    assertEquals(ErrorCode.UNKNOWN_WRITE_MODE, e.getErrorCode());
  }

  @Test
  @DisplayName("关键回归：分页查询仅返回当前用户稿件（复用 findByUserIdOrderByCreateTimeDesc）")
  void page_returnsOnlyOwnArticles() {
    WritingArticle a1 = new WritingArticle();
    a1.setUserId(USER_A);
    a1.setWriteType("dialog");
    Page<WritingArticle> page = new PageImpl<>(List.of(a1));
    when(articleRepository.findByUserIdOrderByCreateTimeDesc(USER_A, PageRequest.of(0, 10)))
        .thenReturn(page);

    Page<WritingArticle> result = service.pageByUser(USER_A, PageRequest.of(0, 10));

    assertEquals(1, result.getTotalElements());
    verify(articleRepository).findByUserIdOrderByCreateTimeDesc(eq(USER_A), any());
  }

  @Test
  @DisplayName("重新编辑：返回历史稿件作为继续迭代的上下文；越权（他人稿件）按不存在拒绝")
  void getForReedit_returnsHistoricalArticle() {
    WritingArticle mine = new WritingArticle();
    mine.setId(1L);
    mine.setUserId(USER_A);
    mine.setArticleContent("历史稿件正文");
    when(articleRepository.findByIdAndUserId(1L, USER_A)).thenReturn(Optional.of(mine));

    WritingArticle context = service.getForReedit(USER_A, 1L);

    assertEquals("历史稿件正文", context.getArticleContent());
    // 越权：B 访问 A 的稿件 → 按不存在处理
    when(articleRepository.findByIdAndUserId(1L, USER_B)).thenReturn(Optional.empty());
    BusinessException e =
        assertThrows(BusinessException.class, () -> service.getForReedit(USER_B, 1L));
    assertEquals(ErrorCode.ARTICLE_NOT_FOUND, e.getErrorCode());
  }

  @Test
  @DisplayName("导出 Word：POI 生成 docx（标题段 + 正文段）")
  void exportWord_generatesDocx() throws IOException {
    WritingArticle a = new WritingArticle();
    a.setId(1L);
    a.setUserId(USER_A);
    a.setArticleTitle("周报标题");
    a.setArticleContent("本周完成工作内容");
    when(articleRepository.findByIdAndUserId(1L, USER_A)).thenReturn(Optional.of(a));

    byte[] docx = service.exportWord(USER_A, 1L);

    // docx 是 zip 容器，以 PK 魔数开头
    assertTrue(docx.length > 4);
    assertTrue(docx[0] == 'P' && docx[1] == 'K', "docx 应以 PK zip 魔数开头");
    // 用 XWPFDocument 回读，断言标题与正文落进 docx
    try (XWPFDocument parsed = new XWPFDocument(new ByteArrayInputStream(docx))) {
      List<XWPFParagraph> paragraphs = parsed.getParagraphs();
      assertEquals("周报标题", paragraphs.get(0).getText());
      assertEquals("本周完成工作内容", paragraphs.get(1).getText());
    }
  }
}

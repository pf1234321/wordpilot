/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.ArticleListItem;
import com.scriptagent.writing.api.dto.ReeditResponse;
import com.scriptagent.writing.business.ArticleService;
import com.scriptagent.writing.common.UserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 稿件历史接口（第 8 节写作编排，writing-api）.
 *
 * <p>端点沿用 CLAUDE.md 接口层字面量：列表 / 详情 / 重新编辑 / 导出 Word / 删除。user_id 唯一来源 {@link
 * UserContext#require()}（H4 不变量①）；详情 / 重新编辑 / 导出 / 删除的越权隔离由 {@link ArticleService} 的 id + user_id
 * 双条件校验兜底（FR-011，越权按不存在处理）。
 */
@RestController
@RequestMapping("/api/article")
public class ArticleController {

  private static final String WORD_MIME =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 100;

  private final ArticleService articleService;

  public ArticleController(ArticleService articleService) {
    this.articleService = articleService;
  }

  /** 稿件列表（分页，仅当前用户，create_time 倒序；返回列表视图，不泄漏持久化实体）. */
  @GetMapping
  public ApiResponse<Page<ArticleListItem>> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    Long userId = UserContext.require();
    int bounded = Math.max(1, Math.min(size, MAX_SIZE));
    Page<ArticleListItem> items =
        articleService.pageByUser(userId, PageRequest.of(page, bounded)).map(ArticleListItem::from);
    return ApiResponse.success(items);
  }

  /** 稿件详情（越权按不存在处理）. */
  @GetMapping("/{id}")
  public ApiResponse<ReeditResponse> detail(@PathVariable Long id) {
    Long userId = UserContext.require();
    return ApiResponse.success(ReeditResponse.from(articleService.getForReedit(userId, id)));
  }

  /** 重新编辑：返回历史稿件作为继续迭代的上下文（越权按不存在处理）. */
  @PostMapping("/{id}/reedit")
  public ApiResponse<ReeditResponse> reedit(@PathVariable Long id) {
    Long userId = UserContext.require();
    return ApiResponse.success(ReeditResponse.from(articleService.getForReedit(userId, id)));
  }

  /** 导出 Word：POI 生成 docx 下载（越权按不存在处理）. */
  @PostMapping(value = "/{id}/export", produces = WORD_MIME)
  public ResponseEntity<byte[]> export(@PathVariable Long id) {
    Long userId = UserContext.require();
    byte[] docx = articleService.exportWord(userId, id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=article-" + id + ".docx")
        .contentType(MediaType.parseMediaType(WORD_MIME))
        .body(docx);
  }

  /** 删除稿件（逻辑删除；越权按不存在处理）. */
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    Long userId = UserContext.require();
    articleService.delete(userId, id);
    return ApiResponse.success();
  }
}

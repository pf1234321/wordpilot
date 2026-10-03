/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.WritingArticle;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 稿件历史数据访问.
 *
 * <p>所有查询 MUST 强制携带 {@code userId}；{@code findByIdAndUserId} 是稿件详情 / 重新编辑 / 导出的统一入口，杜绝仅凭 id
 * 越权访问他人稿件. JPQL 显式带 {@code deleted = false} 以确保逻辑 删除生效（Hibernate 6 {@code @SQLRestriction} 默认不作用于
 * Spring Data 派生查询）.
 */
public interface WritingArticleRepository extends JpaRepository<WritingArticle, Long> {

  @Query(
      "SELECT a FROM WritingArticle a WHERE a.userId = :userId AND a.deleted = 0 ORDER BY"
          + " a.createTime DESC")
  Page<WritingArticle> findByUserIdOrderByCreateTimeDesc(
      @Param("userId") Long userId, Pageable pageable);

  @Query("SELECT a FROM WritingArticle a WHERE a.id = :id AND a.userId = :userId AND a.deleted = 0")
  Optional<WritingArticle> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.WritingArticle;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 稿件 Repository 验收：分页仅当前用户 + 逻辑删除生效. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ArticleRepositoryTest {

  @Autowired private WritingArticleRepository repository;

  @Test
  @DisplayName("分页仅当前用户；逻辑删除生效")
  void findByUserId_returnsOnlyOwnArticlesPaged_logicalDeletedHidden() {
    Long userA = 1L;
    Long userB = 2L;

    persistArticle(userA, "draft-1", "dialog");
    persistArticle(userA, "draft-2", "rag");
    persistArticle(userA, "draft-3", "template");
    persistArticle(userB, "B 稿件", "dialog");

    Pageable firstPage = PageRequest.of(0, 2);
    Page<WritingArticle> firstResult =
        repository.findByUserIdOrderByCreateTimeDesc(userA, firstPage);
    assertThat(firstResult.getTotalElements()).isEqualTo(3L);
    assertThat(firstResult.getContent()).hasSize(2);
    assertThat(firstResult.getContent()).allMatch(a -> userA.equals(a.getUserId()));

    Pageable secondPage = PageRequest.of(1, 2);
    Page<WritingArticle> secondResult =
        repository.findByUserIdOrderByCreateTimeDesc(userA, secondPage);
    assertThat(secondResult.getContent()).hasSize(1);

    Optional<WritingArticle> crossUser =
        repository.findByIdAndUserId(firstResult.getContent().get(0).getId(), userB);
    assertThat(crossUser).isEmpty();
  }

  @Transactional
  WritingArticle persistArticle(Long userId, String title, String writeType) {
    WritingArticle a = new WritingArticle();
    a.setUserId(userId);
    a.setArticleTitle(title);
    a.setArticleContent("正文-" + title);
    a.setWriteType(writeType);
    return repository.saveAndFlush(a);
  }
}

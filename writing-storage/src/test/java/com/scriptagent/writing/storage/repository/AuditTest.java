/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.WritingMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计字段自动填充验收：{@code @CreatedDate} / {@code @LastModifiedDate} 由 {@code AuditEntityListener} 在
 * persist/update 前填充，Service 层不得手填.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AuditTest {

  @Autowired private WritingMemoryRepository repository;

  @Test
  @DisplayName("@CreatedDate 自动填充 create_time，非手填")
  void audit_autoFillsCreateTimeAndUpdateTime() {
    WritingMemory saved = persistAndReturn(1L, "style", "审计测试条目");

    assertThat(saved.getCreateTime()).isNotNull();
    assertThat(saved.getUpdateTime()).isNotNull();
    assertThat(saved.getUpdateTime()).isAfterOrEqualTo(saved.getCreateTime());
  }

  @Transactional
  WritingMemory persistAndReturn(Long userId, String memoryType, String content) {
    WritingMemory m = new WritingMemory();
    m.setUserId(userId);
    m.setMemoryType(memoryType);
    m.setContent(content);
    m.setSource("audit-test");
    return repository.saveAndFlush(m);
  }
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.WritingMemory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 长期记忆 Repository 验收：跨用户隔离 + 逻辑删除后查不到 / 物理行保留. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class WritingMemoryRepositoryTest {

  @Autowired private WritingMemoryRepository repository;
  @PersistenceContext private EntityManager entityManager;

  @Test
  @DisplayName("按 user_id 查、user_id 隔离；逻辑删除后查不到、历史保留")
  void findByUser_returnsOnlyOwnData_noCrossUser() {
    Long userA = 1L;
    Long userB = 2L;

    persistMemory(userA, "style", "A 的风格偏好");
    persistMemory(userB, "term", "B 的术语偏好");

    List<WritingMemory> aList = repository.findByUserId(userA);
    List<WritingMemory> bList = repository.findByUserId(userB);

    assertThat(aList).hasSize(1).allMatch(m -> userA.equals(m.getUserId()));
    assertThat(bList).hasSize(1).allMatch(m -> userB.equals(m.getUserId()));
  }

  @Test
  @Transactional
  @DisplayName("逻辑删除后查不到、历史保留")
  void delete_logical_hiddenFromQuery_rowRetained() {
    Long userA = 1L;
    WritingMemory saved = persistMemory(userA, "style", "待删除条目");
    Long memId = saved.getId();

    repository.deleteById(memId);
    // @SQLDelete 在 deleteById 时通过 EntityManager.remove() 调度；flush 后才真正发出
    // UPDATE writing_memory SET deleted=1 WHERE id=?，物理行被标记但保留.
    entityManager.flush();

    Long physicalCount =
        ((Number)
                entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM writing_memory WHERE id=?")
                    .setParameter(1, memId)
                    .getSingleResult())
            .longValue();
    assertThat(physicalCount).isEqualTo(1L);

    // 清一级缓存，让下次查询走 DB（不被旧实体干扰）.
    entityManager.clear();

    List<WritingMemory> visible = repository.findByUserId(userA);
    assertThat(visible).isEmpty();
  }

  @Transactional
  WritingMemory persistMemory(Long userId, String memoryType, String content) {
    WritingMemory m = new WritingMemory();
    m.setUserId(userId);
    m.setMemoryType(memoryType);
    m.setContent(content);
    m.setSource("test-fixture");
    return repository.saveAndFlush(m);
  }
}

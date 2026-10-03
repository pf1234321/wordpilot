/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.WritingMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 长期记忆数据访问.
 *
 * <p>所有查询 MUST 强制携带 {@code userId}（来源于 {@code UserContext}）， 不带用户条件的查询 不允许暴露. 显式 JPQL 带 {@code
 * deleted = false} 是为绕开 Hibernate 6 {@code @SQLRestriction} 默认不应用于 Spring Data 派生查询的已知行为.
 */
public interface WritingMemoryRepository extends JpaRepository<WritingMemory, Long> {

  @Query(
      "SELECT m FROM WritingMemory m WHERE m.userId = :userId AND m.deleted = 0 ORDER BY m.id DESC")
  List<WritingMemory> findByUserId(@Param("userId") Long userId);

  @Query(
      "SELECT m FROM WritingMemory m WHERE m.userId = :userId AND m.memoryType = :memoryType AND"
          + " m.deleted = 0 ORDER BY m.id DESC")
  List<WritingMemory> findByUserIdAndMemoryType(
      @Param("userId") Long userId, @Param("memoryType") String memoryType);
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.WritingMaterial;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 素材主表数据访问.
 *
 * <p>所有查询 MUST 强制携带 {@code userId}；{@code findByIdAndUserId} 是素材详情 / 预览 / 删除的统一入口. 删除经
 * {@code @SQLDelete} 标记 {@code deleted=1}（物理行不丢），ES 切片由 {@code
 * MaterialChunkRepository.deleteByMaterialId} 同步清理. JPQL 显式带 {@code deleted = false} 以确保逻辑删除生效.
 */
public interface WritingMaterialRepository extends JpaRepository<WritingMaterial, Long> {

  @Query(
      "SELECT m FROM WritingMaterial m WHERE m.userId = :userId AND m.deleted = 0 ORDER BY"
          + " m.createTime DESC")
  Page<WritingMaterial> findByUserId(@Param("userId") Long userId, Pageable pageable);

  @Query(
      "SELECT m FROM WritingMaterial m WHERE m.id = :id AND m.userId = :userId AND m.deleted = 0")
  Optional<WritingMaterial> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}

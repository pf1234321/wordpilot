/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import com.scriptagent.writing.storage.entity.WritingMaterial;
import java.time.LocalDateTime;

/**
 * 素材列表项视图（前端素材管理列表）.
 *
 * <p>列表页不携带正文（正文交给详情 {@link MaterialDetail}），避免把持久化实体直接暴露给客户端 （SpotBugs ENTITY_LEAK
 * 门禁；宪法：不向客户端泄漏持久化属性）。
 */
public record MaterialListItem(
    Long id,
    String fileName,
    String fileType,
    Long fileSize,
    Integer chunkCount,
    LocalDateTime createTime) {

  /** 从持久化实体映射为列表视图（只取列表所需字段）. */
  public static MaterialListItem from(WritingMaterial m) {
    return new MaterialListItem(
        m.getId(),
        m.getFileName(),
        m.getFileType(),
        m.getFileSize(),
        m.getChunkCount(),
        m.getCreateTime());
  }
}

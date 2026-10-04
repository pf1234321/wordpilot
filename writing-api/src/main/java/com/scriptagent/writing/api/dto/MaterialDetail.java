/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import com.scriptagent.writing.storage.entity.WritingMaterial;
import java.time.LocalDateTime;

/**
 * 素材详情/预览响应（前端预览正文）.
 *
 * <p>详情返回解析后的 {@code contentText} 供前端预览；剥离 deleted 等内部字段，仍为列表项的超集。
 */
public record MaterialDetail(
    Long id,
    String fileName,
    String fileType,
    Long fileSize,
    Integer chunkCount,
    String contentText,
    LocalDateTime createTime) {

  /** 由素材实体映射为详情响应（剥离 deleted 等内部字段）. */
  public static MaterialDetail from(WritingMaterial m) {
    return new MaterialDetail(
        m.getId(),
        m.getFileName(),
        m.getFileType(),
        m.getFileSize(),
        m.getChunkCount(),
        m.getContentText(),
        m.getCreateTime());
  }
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.chunk;

/**
 * 切片领域对象：文本智能切片的一个单元.
 *
 * <p>由 {@link ChunkSplitter} 产出，承载切片所属素材 {@code materialId}、切片序号 {@code chunkIndex} 与切片原文 {@code
 * chunkText}；向量化后与 ES 文档 {@code com.scriptagent.writing.storage.es.MaterialChunk} 关联入库. 不可变，
 * 仅作数据传输.
 */
public final class Chunk {

  private final long materialId;
  private final int chunkIndex;
  private final String chunkText;

  public Chunk(long materialId, int chunkIndex, String chunkText) {
    this.materialId = materialId;
    this.chunkIndex = chunkIndex;
    this.chunkText = chunkText;
  }

  public long getMaterialId() {
    return materialId;
  }

  public int getChunkIndex() {
    return chunkIndex;
  }

  public String getChunkText() {
    return chunkText;
  }
}

/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

/**
 * ES 素材切片文档 POJO.
 *
 * <p>非 JPA 实体，是 ES 文档的内存映射；与 MySQL {@code writing_material} 通过 {@code materialId} 关联. 字段命名遵循 ES
 * 默认小写下划线风格（{@code material_id} / {@code chunk_index}），便于 Mapping 声明保持一致.
 */
public class MaterialChunk {

  private String id;
  private Long materialId;
  private Long userId;
  private Integer chunkIndex;
  private String chunkText;
  private float[] embedding;

  public MaterialChunk() {}

  public MaterialChunk(
      String id,
      Long materialId,
      Long userId,
      Integer chunkIndex,
      String chunkText,
      float[] embedding) {
    this.id = id;
    this.materialId = materialId;
    this.userId = userId;
    this.chunkIndex = chunkIndex;
    this.chunkText = chunkText;
    this.embedding = embedding;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getMaterialId() {
    return materialId;
  }

  public void setMaterialId(Long materialId) {
    this.materialId = materialId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Integer getChunkIndex() {
    return chunkIndex;
  }

  public void setChunkIndex(Integer chunkIndex) {
    this.chunkIndex = chunkIndex;
  }

  public String getChunkText() {
    return chunkText;
  }

  public void setChunkText(String chunkText) {
    this.chunkText = chunkText;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }
}

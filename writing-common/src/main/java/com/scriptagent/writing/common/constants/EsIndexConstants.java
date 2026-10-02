/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.constants;

/**
 * Elasticsearch 索引与字段常量.
 *
 * <p>bge-m3 embedding dim 必须为 1024，similarity=cosine， 与 application.yaml 中
 * elasticsearch.index.material_chunk 一致.
 */
public final class EsIndexConstants {

  /** 素材切片索引（默认在 application.yaml 配置） */
  public static final String INDEX_MATERIAL_CHUNK = "writing_material_chunk";

  /** embedding 维度（bge-m3 = 1024） */
  public static final int EMBEDDING_DIM = 1024;

  /** 检索 similarity（cosine / dot / l2） */
  public static final String SIMILARITY = "cosine";

  private EsIndexConstants() {}
}

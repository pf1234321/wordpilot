/*
 * Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0.
 */

/**
 * Elasticsearch 7 访问层：素材切片索引 {@code writing_material_chunk} 的读写、模板库占位.
 *
 * <p>embedding 维度恒等于 {@link
 * com.scriptagent.writing.common.constants.EsIndexConstants#EMBEDDING_DIM}
 * （=1024，bge-m3），similarity = cosine；索引字段禁止硬编码 1024， 须引用上述常量 （constitution 决策七）.
 *
 * <p>检索 MUST 强制带 {@code userId}，删除素材 MUST 按 {@code materialId} 同步清理切片， 不留孤儿向量数据（constitution 原则七）.
 */
package com.scriptagent.writing.storage.es;

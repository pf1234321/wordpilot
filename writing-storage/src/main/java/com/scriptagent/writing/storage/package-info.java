/*
 * Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0.
 */

/**
 * 存储层：JPA Entity / Repository、Redis 操作、ES 操作、文档解析（POI + PDFBox）、切片工具.
 *
 * <p>RAG 管线：解析 → 固定长度 + 重叠切片 → Embedding 向量化 → ES 批量入库 → 用户维度检索.
 */
package com.scriptagent.writing.storage;

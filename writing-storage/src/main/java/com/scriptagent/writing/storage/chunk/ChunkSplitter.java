/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.chunk;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文本智能切片工具：固定长度 + 重叠.
 *
 * <p>起点每次前进 {@code chunkSize - overlap}，保证相邻切片有重叠、语义不断裂；长度 / 重叠从 yml 读取（可调召回质量， 课件"切片参数写死 →
 * 召回质量没法调"的坑点），带默认值兜底. {@code overlap >= chunkSize} 会使步长 ≤ 0 导致死循环，启动时即抛配置错误.
 */
@Component
public class ChunkSplitter {

  @Value("${rag.chunk.size:512}")
  private int chunkSize;

  @Value("${rag.chunk.overlap:50}")
  private int overlap;

  /** 对纯文本切片，返回 {@link Chunk} 列表；空 / null 文本返回空列表（不产生空切片）. */
  public List<Chunk> split(String text, long materialId) {
    if (text == null || text.isEmpty()) {
      return new ArrayList<>();
    }
    int step = chunkSize - overlap;
    if (step <= 0) {
      throw new IllegalStateException(
          "rag.chunk.overlap(" + overlap + ") 必须小于 rag.chunk.size(" + chunkSize + ")，否则切片步长非正");
    }
    List<Chunk> chunks = new ArrayList<>();
    for (int start = 0; start < text.length(); start += step) {
      int end = Math.min(start + chunkSize, text.length());
      chunks.add(new Chunk(materialId, chunks.size(), text.substring(start, end)));
      if (end == text.length()) {
        break;
      }
    }
    return chunks;
  }
}

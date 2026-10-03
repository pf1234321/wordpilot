/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.chunk;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 切片工具验收：固定长度 + 重叠切得对；空文档 / 短文档边界；非法重叠参数防御. */
class ChunkSplitterTest {

  private ChunkSplitter newSplitter(int chunkSize, int overlap) {
    ChunkSplitter splitter = new ChunkSplitter();
    ReflectionTestUtils.setField(splitter, "chunkSize", chunkSize);
    ReflectionTestUtils.setField(splitter, "overlap", overlap);
    return splitter;
  }

  @Test
  @DisplayName("固定长度 + 重叠切得对：起点前进 size-overlap、末片以文本结尾收束")
  void split_createsFixedSizeChunksWithOverlap() {
    ChunkSplitter splitter = newSplitter(10, 2);
    // 18 字符：片1=[0,10)，片2 起点=8 且与片1 重叠 2 字符，末片收束在结尾
    List<Chunk> chunks = splitter.split("abcdefghijklmnopq", 100L);

    assertEquals(2, chunks.size());
    assertEquals("abcdefghij", chunks.get(0).getChunkText());
    assertEquals("ijklmnopq", chunks.get(1).getChunkText());
    assertEquals(0, chunks.get(0).getChunkIndex());
    assertEquals(1, chunks.get(1).getChunkIndex());
    assertTrue(chunks.stream().allMatch(c -> c.getMaterialId() == 100L));
    // 相邻切片重叠 = overlap（片1 尾部 "ij" == 片2 头部 "ij"）
    assertTrue(chunks.get(0).getChunkText().endsWith("ij"));
    assertTrue(chunks.get(1).getChunkText().startsWith("ij"));
  }

  @Test
  @DisplayName("空文档返回空列表，不产生空切片")
  void split_emptyText_returnsEmptyList() {
    ChunkSplitter splitter = newSplitter(512, 50);
    assertTrue(splitter.split("", 1L).isEmpty());
    assertTrue(splitter.split(null, 1L).isEmpty());
  }

  @Test
  @DisplayName("短文档（短于 chunkSize）返回单一切片")
  void split_shortText_returnsSingleChunk() {
    ChunkSplitter splitter = newSplitter(10, 2);
    List<Chunk> chunks = splitter.split("abc", 7L);
    assertEquals(1, chunks.size());
    assertEquals("abc", chunks.get(0).getChunkText());
    assertEquals(0, chunks.get(0).getChunkIndex());
  }

  @Test
  @DisplayName("overlap >= chunkSize 抛配置错误（步长非正会死循环）")
  void split_invalidOverlap_throwsIllegalStateException() {
    ChunkSplitter equal = newSplitter(10, 10);
    assertThrows(IllegalStateException.class, () -> equal.split("abcdefghij", 1L));
    ChunkSplitter greater = newSplitter(10, 12);
    assertThrows(IllegalStateException.class, () -> greater.split("abcdefghij", 1L));
  }
}

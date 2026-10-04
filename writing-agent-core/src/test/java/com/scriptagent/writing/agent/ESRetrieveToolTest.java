/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.es.MaterialChunkRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RAG 检索工具单测：经模型服务 embed 向量化、按 user_id 检索、user_id 来源 UserContext（宪法三/五）. */
@ExtendWith(MockitoExtension.class)
class ESRetrieveToolTest {

  private static final Long USER_ID = 1L;

  @Mock private ModelService modelService;
  @Mock private MaterialChunkRepository materialChunkRepository;

  private ESRetrieveTool tool;

  @BeforeEach
  void setUp() {
    tool = new ESRetrieveTool(modelService, materialChunkRepository);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  @DisplayName("execute：先 ModelService.embed 向量化，再按 user_id 检索 TopN，返回切片文本")
  void execute_embedsQueryThenSearchesWithUserId() throws Exception {
    UserContext.set(USER_ID);
    float[] vec = new float[] {0.1f, 0.2f};
    when(modelService.embed("查新品推文")).thenReturn(vec);
    when(materialChunkRepository.search(USER_ID, vec, 5)).thenReturn(List.of("切片一", "切片二"));
    assertEquals("切片一\n切片二", tool.execute("查新品推文"));
    verify(modelService).embed("查新品推文");
    verify(materialChunkRepository).search(USER_ID, vec, 5);
  }

  @Test
  @DisplayName("user_id 一律来自 UserContext.require()，未登录抛错、绝不从前端参数取")
  void execute_requiresUserContext() {
    UserContext.clear();
    assertThrows(IllegalStateException.class, () -> tool.execute("查新品推文"));
  }

  @Test
  void execute_emptyHits_returnsEmptyReference() throws Exception {
    UserContext.set(USER_ID);
    when(modelService.embed(any())).thenReturn(new float[] {0.1f});
    when(materialChunkRepository.search(eq(USER_ID), any(), eq(5))).thenReturn(List.of());
    assertEquals("", tool.execute("查新品推文"));
  }

  @Test
  @DisplayName("带显式 userId 的入口：SSE 异步线程无 ThreadLocal 也能按传入 userId 检索")
  void execute_withExplicitUserId_usesPassedUserId() throws Exception {
    UserContext.clear(); // 模拟 SSE 独立线程：请求线程的 ThreadLocal 不跨线程
    float[] vec = new float[] {0.3f, 0.4f};
    when(modelService.embed("查新品推文")).thenReturn(vec);
    when(materialChunkRepository.search(USER_ID, vec, 5)).thenReturn(List.of("切片A"));
    assertEquals("切片A", tool.execute(USER_ID, "查新品推文"));
    verify(materialChunkRepository).search(USER_ID, vec, 5);
  }
}

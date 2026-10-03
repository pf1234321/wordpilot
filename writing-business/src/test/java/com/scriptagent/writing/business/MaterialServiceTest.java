/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.chunk.Chunk;
import com.scriptagent.writing.storage.chunk.ChunkSplitter;
import com.scriptagent.writing.storage.entity.WritingMaterial;
import com.scriptagent.writing.storage.es.MaterialChunkRepository;
import com.scriptagent.writing.storage.parser.DocumentParser;
import com.scriptagent.writing.storage.repository.WritingMaterialRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 素材 RAG 编排验收：上传全链路成功；向量化失败回退不产生半成品；删除同步清 ES + 逻辑删除；越权删除按不存在处理. */
@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

  @Mock private DocumentParser documentParser;
  @Mock private ChunkSplitter chunkSplitter;
  @Mock private ModelService modelService;
  @Mock private MaterialChunkRepository materialChunkRepository;
  @Mock private WritingMaterialRepository materialRepository;

  @Captor private ArgumentCaptor<List<Chunk>> chunkCaptor;
  @Captor private ArgumentCaptor<float[][]> vectorCaptor;

  private MaterialService service;

  @BeforeEach
  void setUp() {
    service =
        new MaterialService(
            documentParser,
            chunkSplitter,
            modelService,
            materialChunkRepository,
            materialRepository);
  }

  @Test
  @DisplayName("上传全链路：解析→保存主记录→切片→向量化→ES 批量入库→更新切片数")
  void upload_fullPipeline_savesMaterialAndUpsertsChunks() throws Exception {
    UserContext.set(42L);
    try {
      ByteArrayInputStream in = new ByteArrayInputStream("text".getBytes(StandardCharsets.UTF_8));
      when(documentParser.parse("a.txt", in)).thenReturn("素材正文");
      when(chunkSplitter.split("素材正文", 1L)).thenReturn(chunksOfMaterial(1L, 2));
      when(modelService.embed(anyString())).thenReturn(new float[1024]);
      when(materialRepository.save(any(WritingMaterial.class)))
          .thenAnswer(
              inv -> {
                WritingMaterial m = inv.getArgument(0);
                if (m.getId() == null) {
                  m.setId(1L);
                }
                return m;
              });

      Long id = service.upload("a.txt", in);

      assertEquals(1L, id);
      // ES 收到与切片数一致的 chunks + vectors
      verify(materialChunkRepository).batchUpsert(chunkCaptor.capture(), vectorCaptor.capture());
      assertEquals(2, chunkCaptor.getValue().size());
      assertEquals(2, vectorCaptor.getValue().length);
      // 主记录保存两次：落主记录 + 更新 chunk_count
      verify(materialRepository, times(2)).save(any(WritingMaterial.class));
    } finally {
      UserContext.clear();
    }
  }

  @Test
  @DisplayName("向量化失败整体回退：抛异常、ES 未写、主记录不持久化（无半成品）")
  void upload_embeddingFailure_rollsBackNoPartialData() throws Exception {
    UserContext.set(42L);
    try {
      when(documentParser.parse(anyString(), any())).thenReturn("正文");
      when(chunkSplitter.split(anyString(), anyLong())).thenReturn(chunksOfMaterial(5L, 1));
      when(materialRepository.save(any(WritingMaterial.class)))
          .thenAnswer(
              inv -> {
                WritingMaterial m = inv.getArgument(0);
                m.setId(5L);
                return m;
              });
      when(modelService.embed(anyString())).thenThrow(new IllegalStateException("模型未加载"));

      assertThrows(IllegalStateException.class, () -> service.upload("a.txt", in("x")));
      verify(materialChunkRepository, never()).batchUpsert(any(), any());
      // 仅首次 save（后续 chunk_count 更新未发生——事务回滚，主记录不持久化）
      verify(materialRepository, times(1)).save(any(WritingMaterial.class));
    } finally {
      UserContext.clear();
    }
  }

  @Test
  @DisplayName("不支持格式抛 UNSUPPORTED_FORMAT，未落任何数据")
  void upload_unsupportedFormat_throwsAndNoSave() {
    UserContext.set(42L);
    try {
      when(documentParser.parse(anyString(), any()))
          .thenThrow(new BusinessException(ErrorCode.UNSUPPORTED_FORMAT));

      BusinessException e =
          assertThrows(
              BusinessException.class, () -> service.upload("legacy.doc", in("not a text")));
      assertEquals(ErrorCode.UNSUPPORTED_FORMAT, e.getErrorCode());
      verify(materialRepository, never()).save(any());
    } finally {
      UserContext.clear();
    }
  }

  @Test
  @DisplayName("删除素材：先按 material_id 清 ES 切片，再 MySQL 逻辑删除")
  void deleteMaterial_removesAllEsChunks() throws Exception {
    UserContext.set(42L);
    try {
      WritingMaterial material = new WritingMaterial();
      material.setId(9L);
      when(materialRepository.findByIdAndUserId(9L, 42L)).thenReturn(Optional.of(material));

      service.delete(9L);

      verify(materialChunkRepository).deleteByMaterialId(9L);
      verify(materialRepository).delete(material);
    } finally {
      UserContext.clear();
    }
  }

  @Test
  @DisplayName("ES 切片清理失败则中止，MySQL 主记录不删除")
  void delete_esFailure_abortsBeforeMySqlDelete() throws Exception {
    UserContext.set(42L);
    try {
      WritingMaterial material = new WritingMaterial();
      material.setId(11L);
      when(materialRepository.findByIdAndUserId(11L, 42L)).thenReturn(Optional.of(material));
      doThrow(new IOException("es down")).when(materialChunkRepository).deleteByMaterialId(11L);

      assertThrows(BusinessException.class, () -> service.delete(11L));
      verify(materialRepository, never()).delete(material);
    } finally {
      UserContext.clear();
    }
  }

  @Test
  @DisplayName("越权删除他人素材：按不存在处理，抛 MATERIAL_NOT_FOUND，无任何删除动作")
  void delete_otherUsersMaterial_throwsNotFound() throws Exception {
    UserContext.set(42L);
    try {
      when(materialRepository.findByIdAndUserId(999L, 42L)).thenReturn(Optional.empty());

      BusinessException e = assertThrows(BusinessException.class, () -> service.delete(999L));
      assertEquals(ErrorCode.MATERIAL_NOT_FOUND, e.getErrorCode());
      verify(materialChunkRepository, never()).deleteByMaterialId(anyLong());
      verify(materialRepository, never()).delete(any());
    } finally {
      UserContext.clear();
    }
  }

  private static List<Chunk> chunksOfMaterial(long materialId, int n) {
    return List.of(new Chunk(materialId, 0, "c0"), new Chunk(materialId, 1, "c1")).subList(0, n);
  }

  private static ByteArrayInputStream in(String s) {
    return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
  }
}

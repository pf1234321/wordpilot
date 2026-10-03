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
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 素材 RAG 全链路编排（宪法原则七）：上传 → 解析 → 保存 MySQL 主记录 → 切片 → 向量化（经 {@link ModelService} 唯一出口，不直连
 * EmbeddingClient）→ ES 批量入库；删除 → 校验归属 → 先清 ES 切片 → 再 MySQL 逻辑删除.
 *
 * <p>userId 唯一来源 {@link UserContext#require()}（H4 不变量①，禁止前端参数取，防越权）；任一步失败抛 {@link RuntimeException}
 * 包装，触发 {@code @Transactional} 回滚——不产生孤儿数据. ES 受检异常（{@link IOException}） 统一转 {@link
 * BusinessException}，否则受检异常不会触发 Spring 事务回滚.
 */
@Service
public class MaterialService {

  private final DocumentParser documentParser;
  private final ChunkSplitter chunkSplitter;
  private final ModelService modelService;
  private final MaterialChunkRepository materialChunkRepository;
  private final WritingMaterialRepository materialRepository;

  public MaterialService(
      DocumentParser documentParser,
      ChunkSplitter chunkSplitter,
      ModelService modelService,
      MaterialChunkRepository materialChunkRepository,
      WritingMaterialRepository materialRepository) {
    this.documentParser = documentParser;
    this.chunkSplitter = chunkSplitter;
    this.modelService = modelService;
    this.materialChunkRepository = materialChunkRepository;
    this.materialRepository = materialRepository;
  }

  /**
   * 上传素材：解析 → 落 MySQL 主记录 → 切片 → 向量化 → ES 批量入库 → 更新切片数，返回素材 id.
   *
   * <p>Controller 层（第 8 节）将 {@code MultipartFile} 转为 {@code (fileName, InputStream)} 调用，本层不依赖
   * Servlet API.
   */
  @Transactional
  public Long upload(String originalFilename, InputStream in) {
    Long userId = UserContext.require();
    String text = documentParser.parse(originalFilename, in);

    // 先落主记录拿到 materialId，再切片向量化入库；任一步失败 @Transactional 回滚，无孤儿
    WritingMaterial material = new WritingMaterial();
    material.setUserId(userId);
    material.setFileName(originalFilename);
    material.setFileType(extensionOf(originalFilename));
    material.setContentText(text);
    material.setChunkCount(0);
    WritingMaterial saved = materialRepository.save(material);

    List<Chunk> chunks = chunkSplitter.split(text, saved.getId());
    if (!chunks.isEmpty()) {
      float[][] vectors = new float[chunks.size()][];
      for (int i = 0; i < chunks.size(); i++) {
        vectors[i] = modelService.embed(chunks.get(i).getChunkText());
      }
      try {
        materialChunkRepository.batchUpsert(chunks, vectors);
      } catch (IOException e) {
        throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
      }
    }
    saved.setChunkCount(chunks.size());
    return materialRepository.save(saved).getId();
  }

  /** 删除素材：先按 {@code material_id} 清理 ES 全部切片（失败则中止，MySQL 不动），再 MySQL 逻辑删除（{@code @SQLDelete} 打标）. */
  @Transactional
  public void delete(Long materialId) {
    Long userId = UserContext.require();
    WritingMaterial material =
        materialRepository
            .findByIdAndUserId(materialId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND));
    try {
      materialChunkRepository.deleteByMaterialId(materialId);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
    }
    materialRepository.delete(material);
  }

  /** 提取小写扩展名（与 {@link DocumentParser} 语义一致，避免跨类暴露私有实现）. */
  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}

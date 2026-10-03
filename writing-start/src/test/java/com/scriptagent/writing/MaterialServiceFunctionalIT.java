/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing;

import com.scriptagent.writing.business.MaterialService;
import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.entity.WritingMaterial;
import com.scriptagent.writing.storage.es.MaterialChunk;
import com.scriptagent.writing.storage.es.MaterialChunkRepository;
import com.scriptagent.writing.storage.repository.WritingMaterialRepository;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.scriptagent.writing.common.constants.EsIndexConstants.INDEX_MATERIAL_CHUNK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实素材端到端功能测试（本地 MySQL/ES/BGE 中间件；一次性运行，非 CI 回归）.
 *
 * <p>用 {@code docs/测试素材} 下 docx/pdf/txt/md 真实文档跑完整 RAG 链路：upload → MySQL 主记录 + ES 切片 → 向量化检索召回 →
 * delete 清理无残留. 需环境变量：{@code DB_PASSWORD}/{@code BGE_MODEL_PATH}/{@code DASHSCOPE_API_KEY}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
class MaterialServiceFunctionalIT {

  private static final Logger log = LoggerFactory.getLogger(MaterialServiceFunctionalIT.class);

  private static final long TEST_USER = 777001L;
  private static final String BASE = "/Users/code/java/wordpilot/docs/测试素材";

  @Autowired MaterialService materialService;
  @Autowired MaterialChunkRepository chunkRepo;
  @Autowired WritingMaterialRepository materialRepo;
  @Autowired ModelService modelService;
  @Autowired RestHighLevelClient esClient;

  @BeforeEach
  void setUp() throws Exception {
    UserContext.set(TEST_USER);
    chunkRepo.ensureIndex();
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  /** 强制刷新 ES 索引，消除 bulk 写入默认 refresh 延迟（~1s）导致的"刚写入查不到"时序. */
  private void refreshIndex() throws IOException {
    esClient.indices().refresh(new RefreshRequest(INDEX_MATERIAL_CHUNK), RequestOptions.DEFAULT);
  }

  @Test
  @DisplayName("真实素材端到端：docx/pdf/txt/md 上传→主记录+ES切片→向量检索召回→删除无残留")
  void upload_search_delete_realSamples() throws Exception {
    File dir = new File(BASE);
    assertTrue(dir.isDirectory(), "测试素材目录不存在: " + BASE);
    File[] files = dir.listFiles();
    assertTrue(files != null && files.length >= 4, "测试素材目录为空");

    List<Long> uploaded = new ArrayList<>();
    for (File f : files) {
      Long id =
          materialService.upload(
              f.getName(), new ByteArrayInputStream(Files.readAllBytes(f.toPath())));
      uploaded.add(id);

      WritingMaterial saved = materialRepo.findByIdAndUserId(id, TEST_USER).orElseThrow();
      assertNotNull(saved.getContentText());
      assertFalse(saved.getContentText().isBlank(), f.getName() + " 主记录原文为空");
      assertEquals(f.getName(), saved.getFileName());
      assertTrue(saved.getChunkCount() >= 1, f.getName() + " 切片数应为正");

      refreshIndex(); // 消除 bulk 写入 refresh 延迟后，再断言 ES 切片
      List<MaterialChunk> chunks = chunkRepo.findByMaterialId(id);
      assertEquals(saved.getChunkCount(), chunks.size(), f.getName() + " ES切片数与主记录不一致");
      log.info(
          "[func] upload "
              + f.getName()
              + " → materialId="
              + id
              + " | 原文长度="
              + saved.getContentText().length()
              + " | 切片数="
              + chunks.size());
    }

    // 检索：以素材4(md) 开头文本作查询，同模型向量化后应在该用户切片内召回（命中素材4 的切片）
    refreshIndex();
    String query = "AI 写作引擎从 0 到 1：原理与工程实践";
    List<String> hits = chunkRepo.search(TEST_USER, modelService.embed(query), 3);
    assertFalse(hits.isEmpty(), "检索应召回切片");
    log.info("[func] search 查询='" + query + "' 召回 " + hits.size() + " 片:");
    for (String h : hits) {
      log.info("[func]   召回片段: " + (h.length() > 30 ? h.substring(0, 30) + "…" : h));
    }

    // 删除全部上传素材，验证 ES 无残留 + MySQL 逻辑删除后查不到
    for (Long id : uploaded) {
      materialService.delete(id);
      refreshIndex(); // deleteByQuery 默认也不 refresh，刷新后再断言无残留
      assertTrue(chunkRepo.findByMaterialId(id).isEmpty(), "materialId=" + id + " 删除后 ES 应有残留");
      assertFalse(
          materialRepo.findByIdAndUserId(id, TEST_USER).isPresent(),
          "materialId=" + id + " 删除后 MySQL 应查不到");
    }
    log.info("[func] delete 全部素材 " + uploaded.size() + " 份，ES 无残留、MySQL 逻辑删除均通过");
  }
}

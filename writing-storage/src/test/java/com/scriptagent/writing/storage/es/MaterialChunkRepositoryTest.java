/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.constants.EsIndexConstants;
import com.scriptagent.writing.storage.chunk.Chunk;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 素材切片 ES 验收：索引建好（dim=1024 cosine）、按 userId 检索隔离、按 materialId 清理.
 *
 * <p>依赖本地 ES 7.17 服务，标记 {@code @Tag("integration")} CI 跳过、本地人工跑. 启动前确保 {@code elasticsearch.uris}
 * 配置正确（默认 {@code http://127.0.0.1:9200}）.
 */
@SpringBootTest
@Tag("integration")
class MaterialChunkRepositoryTest {

  @Autowired private MaterialChunkRepository repository;
  @Autowired private RestHighLevelClient client;

  @Test
  @DisplayName("ES 启动建索引 dim=1024 cosine")
  void ensureIndex_createsWritingMaterialChunkedWithDim1024Cosine() throws Exception {
    repository.ensureIndex();

    int dim = MaterialChunkRepository.embeddingDim();
    assertThat(dim).isEqualTo(EsIndexConstants.EMBEDDING_DIM).isEqualTo(1024);
    assertThat(EsIndexConstants.SIMILARITY).isEqualTo("cosine");
  }

  @Test
  @DisplayName("ES 检索带 user_id")
  void searchByUser_filtersByUserId() throws Exception {
    repository.ensureIndex();
    long userA = 9001L;
    long userB = 9002L;

    long materialA = 91001L;
    long materialB = 91002L;

    float[] vec = randomVector();
    List<MaterialChunk> aChunks = chunks(materialA, vec, 3);
    List<MaterialChunk> bChunks = chunks(materialB, vec, 2);

    repository.saveChunks(userA, materialA, aChunks);
    repository.saveChunks(userB, materialB, bChunks);
    refreshIndex();

    List<MaterialChunk> aHits = repository.searchByUser(userA, vec, 10);
    List<MaterialChunk> bHits = repository.searchByUser(userB, vec, 10);

    assertThat(aHits).hasSize(3).allMatch(c -> userA == c.getUserId());
    assertThat(bHits).hasSize(2).allMatch(c -> userB == c.getUserId());

    repository.deleteByMaterialId(materialA);
    repository.deleteByMaterialId(materialB);
    refreshIndex();
  }

  @Test
  @DisplayName("删除按 material_id 清干净")
  void deleteByMaterialId_removesAllChunks() throws Exception {
    repository.ensureIndex();
    long user = 9101L;
    long materialX = 92001L;
    long materialY = 92002L;

    repository.saveChunks(user, materialX, chunks(materialX, randomVector(), 3));
    repository.saveChunks(user, materialY, chunks(materialY, randomVector(), 2));
    refreshIndex();

    List<MaterialChunk> before = repository.searchByUser(user, randomVector(), 50);
    assertThat(before).hasSize(5);

    repository.deleteByMaterialId(materialX);
    refreshIndex();

    List<MaterialChunk> afterX = repository.searchByUser(user, randomVector(), 50);
    assertThat(afterX).hasSize(2).allMatch(c -> materialY == c.getMaterialId());

    repository.deleteByMaterialId(materialY);
  }

  @Test
  @DisplayName("batchUpsert 批量写入含 user_id，可被该用户检索")
  void batchUpsert_writesChunksWithUserId() throws Exception {
    repository.ensureIndex();
    try {
      UserContext.set(9101L);
      List<Chunk> chunks = List.of(new Chunk(95001L, 0, "up-0"), new Chunk(95001L, 1, "up-1"));
      repository.batchUpsert(chunks, new float[][] {randomVector(), randomVector()});
    } finally {
      UserContext.clear();
    }
    refreshIndex();

    List<String> hits = repository.search(9101L, randomVector(), 10);
    assertThat(hits).hasSize(2).containsExactlyInAnyOrder("up-0", "up-1");

    repository.deleteByMaterialId(95001L);
    refreshIndex();
  }

  @Test
  @DisplayName("检索强制 user_id：search(userB, vec, 5) 只应召回 userB 的素材")
  void search_carriesUserId_returnsOnlyOwnChunks() throws Exception {
    repository.ensureIndex();
    try {
      UserContext.set(9001L);
      repository.batchUpsert(
          List.of(new Chunk(93001L, 0, "userA素材-0"), new Chunk(93001L, 1, "userA素材-1")),
          new float[][] {randomVector(), randomVector()});
      UserContext.set(9002L);
      repository.batchUpsert(
          List.of(new Chunk(93002L, 0, "userB素材-0")), new float[][] {randomVector()});
    } finally {
      UserContext.clear();
    }
    refreshIndex();

    // userB 检索只应命中 userB 的切片，userA 数据即使已写入也不泄漏
    List<String> bHits = repository.search(9002L, randomVector(), 5);
    assertThat(bHits).hasSize(1).allMatch(t -> t.startsWith("userB"));

    repository.deleteByMaterialId(93001L);
    repository.deleteByMaterialId(93002L);
    refreshIndex();
  }

  @Test
  @DisplayName("删除素材按 material_id 清干净：findByMaterialId 为空，不留孤儿向量")
  void deleteMaterial_removesAllEsChunks() throws Exception {
    repository.ensureIndex();
    try {
      UserContext.set(9100L);
      repository.batchUpsert(
          List.of(
              new Chunk(94001L, 0, "x-0"),
              new Chunk(94001L, 1, "x-1"),
              new Chunk(94001L, 2, "x-2")),
          new float[][] {randomVector(), randomVector(), randomVector()});
    } finally {
      UserContext.clear();
    }
    refreshIndex();

    assertThat(repository.findByMaterialId(94001L)).hasSize(3);

    repository.deleteByMaterialId(94001L);
    refreshIndex();

    // 不清理 → 孤儿向量残留，后续搜索串数据
    assertThat(repository.findByMaterialId(94001L)).isEmpty();
  }

  private static float[] randomVector() {
    Random r = new Random();
    float[] v = new float[EsIndexConstants.EMBEDDING_DIM];
    for (int i = 0; i < v.length; i++) {
      v[i] = r.nextFloat();
    }
    return v;
  }

  private static List<MaterialChunk> chunks(long materialId, float[] vec, int n) {
    List<MaterialChunk> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      MaterialChunk c = new MaterialChunk();
      c.setChunkIndex(i);
      c.setChunkText("chunk-" + materialId + "-" + i);
      c.setEmbedding(vec);
      list.add(c);
    }
    return list;
  }

  private void refreshIndex() throws Exception {
    client
        .indices()
        .refresh(new RefreshRequest(EsIndexConstants.INDEX_MATERIAL_CHUNK), RequestOptions.DEFAULT);
    // 抑制未使用反射告警（保留以备调试）
    @SuppressWarnings("unused")
    Field f = MaterialChunkRepository.class.getDeclaredField("client");
  }
}

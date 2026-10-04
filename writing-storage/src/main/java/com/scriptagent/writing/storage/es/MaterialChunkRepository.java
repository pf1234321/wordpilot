/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.constants.EsIndexConstants;
import com.scriptagent.writing.storage.chunk.Chunk;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import static com.scriptagent.writing.common.constants.EsIndexConstants.EMBEDDING_DIM;
import static com.scriptagent.writing.common.constants.EsIndexConstants.INDEX_MATERIAL_CHUNK;
import static com.scriptagent.writing.common.constants.EsIndexConstants.SIMILARITY;

/**
 * 素材切片 ES 仓库.
 *
 * <p>职责：建索引（含 dim=1024 cosine mapping）、按 userId 写入切片、按 userId + 向量 cosine 相似度检索、按 materialId 清理切片.
 * 所有 API MUST 强制携带 {@code userId} / {@code materialId}，杜绝跨用户 / 孤儿数据.
 *
 * <p>embedding dim 引用 {@link EsIndexConstants#EMBEDDING_DIM} 常量，禁止硬编码 1024 （constitution 决策七）.
 */
@Repository
public class MaterialChunkRepository {

  private static final Logger log = LoggerFactory.getLogger(MaterialChunkRepository.class);

  private static final String COSINE_PAINSCRIPT =
      "double dot=0.0; double nq=0.0; double ne=0.0;"
          + " for (int i=0; i<params.qv.length; i++) {"
          + "   dot += params.qv[i] * doc['embedding'].vectorValue[i];"
          + "   nq  += params.qv[i] * params.qv[i];"
          + "   ne  += doc['embedding'].vectorValue[i] * doc['embedding'].vectorValue[i];"
          + " }"
          + " if (nq == 0.0 || ne == 0.0) return 0.0;"
          + " return dot / Math.sqrt(nq * ne);";

  private final RestHighLevelClient client;

  public MaterialChunkRepository(RestHighLevelClient client) {
    this.client = client;
  }

  /** 创建 {@code writing_material_chunk} 索引，含 dim=1024 cosine mapping（已存在则跳过）. */
  public void ensureIndex() throws IOException {
    boolean exists =
        client.indices().exists(new GetIndexRequest(INDEX_MATERIAL_CHUNK), RequestOptions.DEFAULT);
    if (exists) {
      log.info("ES 索引已存在：{}", INDEX_MATERIAL_CHUNK);
      return;
    }
    CreateIndexRequest request = new CreateIndexRequest(INDEX_MATERIAL_CHUNK);
    request.source(mappingJson(), XContentType.JSON);
    client.indices().create(request, RequestOptions.DEFAULT);
    log.info(
        "ES 索引已创建：{} (dim={}, similarity={})", INDEX_MATERIAL_CHUNK, EMBEDDING_DIM, SIMILARITY);
  }

  /** 批量写入素材切片（同一素材下所有切片一次写入），doc id = {@code "{materialId}_{chunkIndex}"}. */
  public void saveChunks(Long userId, Long materialId, List<MaterialChunk> chunks)
      throws IOException {
    if (chunks == null || chunks.isEmpty()) {
      return;
    }
    BulkRequest bulk = new BulkRequest();
    // 写后读一致：切片入库立即 refresh，保证上传后立即 rag 检索（向量相似度）能命中（全链路联调修复）
    bulk.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    for (MaterialChunk chunk : chunks) {
      chunk.setUserId(userId);
      chunk.setMaterialId(materialId);
      if (chunk.getId() == null) {
        chunk.setId(materialId + "_" + chunk.getChunkIndex());
      }
      bulk.add(toIndexRequest(chunk));
    }
    BulkResponse response = client.bulk(bulk, RequestOptions.DEFAULT);
    if (response.hasFailures()) {
      throw new IOException("ES bulk 写入失败：" + response.buildFailureMessage());
    }
  }

  /**
   * 按 userId + 向量 cosine 相似度检索 TopK 切片.
   *
   * <p>script_score + 内置 cosine 计算（ES 7.x 全版本支持）；filter 强制 userId 防越权.
   */
  public List<MaterialChunk> searchByUser(Long userId, float[] queryVector, int topK)
      throws IOException {
    SearchRequest request = new SearchRequest(INDEX_MATERIAL_CHUNK);
    SearchSourceBuilder source = new SearchSourceBuilder();

    Map<String, Object> params = new HashMap<>();
    params.put("qv", toBoxed(queryVector));
    Script script = new Script(ScriptType.INLINE, "painless", COSINE_PAINSCRIPT, params);

    source.query(
        QueryBuilders.boolQuery()
            .filter(QueryBuilders.termQuery("user_id", userId))
            .must(QueryBuilders.scriptScoreQuery(QueryBuilders.matchAllQuery(), script)));
    source.size(topK);

    request.source(source);
    SearchResponse response = client.search(request, RequestOptions.DEFAULT);

    List<MaterialChunk> result = new ArrayList<>();
    for (SearchHit hit : response.getHits().getHits()) {
      MaterialChunk chunk = new MaterialChunk();
      chunk.setId(hit.getId());
      Map<String, Object> src = hit.getSourceAsMap();
      if (src.get("material_id") instanceof Number n) {
        chunk.setMaterialId(n.longValue());
      }
      if (src.get("user_id") instanceof Number n) {
        chunk.setUserId(n.longValue());
      }
      if (src.get("chunk_index") instanceof Number n) {
        chunk.setChunkIndex(n.intValue());
      }
      chunk.setChunkText((String) src.get("chunk_text"));
      result.add(chunk);
    }
    return result;
  }

  /**
   * 素材全链路入库入口：把切片 + 对应向量批量写入 ES（第 4 节 {@code MaterialService} 编排调用）.
   *
   * <p>userId 来源 {@link UserContext#require()}（H4 不变量①，禁止前端参数取）；doc id = {@code
   * {materialId}_{chunkIndex}}； 复用 {@link #saveChunks} 的 bulk 写入路径，同一素材一次批量. 空切片直接返回，不产生空 bulk.
   *
   * @param chunks 切片列表（同一素材）
   * @param vectors 与 {@code chunks} 一一对应的 1024 维向量
   */
  public void batchUpsert(List<Chunk> chunks, float[][] vectors) throws IOException {
    if (chunks == null || chunks.isEmpty()) {
      return;
    }
    if (vectors == null || vectors.length != chunks.size()) {
      throw new IllegalArgumentException(
          "vectors 数量("
              + (vectors == null ? 0 : vectors.length)
              + ") 必须与 chunks("
              + chunks.size()
              + ") 一致");
    }
    Long userId = UserContext.require();
    List<MaterialChunk> docs = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      Chunk c = chunks.get(i);
      MaterialChunk mc = new MaterialChunk();
      mc.setMaterialId(c.getMaterialId());
      mc.setChunkIndex(c.getChunkIndex());
      mc.setChunkText(c.getChunkText());
      mc.setEmbedding(vectors[i]);
      docs.add(mc);
    }
    saveChunks(userId, chunks.get(0).getMaterialId(), docs);
  }

  /**
   * 按 userId + 向量余弦相似度检索 TopK 切片文本（第 4 节 {@code MaterialService} / 第 5 节 ESRetrieveTool 用）.
   *
   * <p>返回切片原文列表；filter 强制 userId 防越权——只召回当前用户的素材片段（关键回归 {@code
   * search_carriesUserId_returnsOnlyOwnChunks}）.
   */
  public List<String> search(Long userId, float[] queryVector, int topK) throws IOException {
    List<MaterialChunk> hits = searchByUser(userId, queryVector, topK);
    List<String> texts = new ArrayList<>(hits.size());
    for (MaterialChunk hit : hits) {
      texts.add(hit.getChunkText());
    }
    return texts;
  }

  /** 按 materialId 查询该素材下全部切片（删除后验证清空，杜绝孤儿数据；{@code MaterialService.delete} 配套）. */
  public List<MaterialChunk> findByMaterialId(Long materialId) throws IOException {
    SearchRequest request = new SearchRequest(INDEX_MATERIAL_CHUNK);
    SearchSourceBuilder source = new SearchSourceBuilder();
    source.query(QueryBuilders.termQuery("material_id", materialId));
    source.size(10000);
    request.source(source);
    SearchResponse response = client.search(request, RequestOptions.DEFAULT);
    List<MaterialChunk> result = new ArrayList<>();
    for (SearchHit hit : response.getHits().getHits()) {
      result.add(fromHit(hit));
    }
    return result;
  }

  /** SearchHit → MaterialChunk（仅供新方法 {@link #findByMaterialId} 使用；既有方法保持原样不动）. */
  private static MaterialChunk fromHit(SearchHit hit) {
    MaterialChunk chunk = new MaterialChunk();
    chunk.setId(hit.getId());
    Map<String, Object> src = hit.getSourceAsMap();
    if (src.get("material_id") instanceof Number n) {
      chunk.setMaterialId(n.longValue());
    }
    if (src.get("user_id") instanceof Number n) {
      chunk.setUserId(n.longValue());
    }
    if (src.get("chunk_index") instanceof Number n) {
      chunk.setChunkIndex(n.intValue());
    }
    chunk.setChunkText((String) src.get("chunk_text"));
    return chunk;
  }

  /** 按 materialId 删除该素材下全部切片（与 MySQL 逻辑删除同步）. */
  public void deleteByMaterialId(Long materialId) throws IOException {
    DeleteByQueryRequest request =
        new DeleteByQueryRequest(INDEX_MATERIAL_CHUNK)
            .setQuery(QueryBuilders.termQuery("material_id", materialId));
    client.deleteByQuery(request, RequestOptions.DEFAULT);
  }

  private IndexRequest toIndexRequest(MaterialChunk chunk) {
    Map<String, Object> source = new HashMap<>();
    source.put("material_id", chunk.getMaterialId());
    source.put("user_id", chunk.getUserId());
    source.put("chunk_index", chunk.getChunkIndex());
    source.put("chunk_text", chunk.getChunkText());
    source.put("embedding", chunk.getEmbedding());
    return new IndexRequest(INDEX_MATERIAL_CHUNK)
        .id(chunk.getId())
        .source(source, XContentType.JSON);
  }

  private String mappingJson() {
    return "{"
        + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
        + "\"mappings\":{"
        + "  \"properties\":{"
        + "    \"material_id\":{\"type\":\"long\"},"
        + "    \"user_id\":{\"type\":\"long\"},"
        + "    \"chunk_index\":{\"type\":\"integer\"},"
        + "    \"chunk_text\":{\"type\":\"text\"},"
        + "    \"embedding\":{\"type\":\"dense_vector\",\"dims\":"
        + EMBEDDING_DIM
        + ",\"index\":false,\"similarity\":\""
        + SIMILARITY
        + "\"}"
        + "  }"
        + "}}";
  }

  /** float[] → Float[]，便于 painless 脚本访问（dense_vector 数组为 float）. */
  private static Float[] toBoxed(float[] arr) {
    if (arr == null) {
      return new Float[0];
    }
    Float[] out = new Float[arr.length];
    for (int i = 0; i < arr.length; i++) {
      out[i] = arr[i];
    }
    return out;
  }

  /** 测试用：返回 embedding dim 常量以便断言. */
  static int embeddingDim() {
    return EMBEDDING_DIM;
  }
}

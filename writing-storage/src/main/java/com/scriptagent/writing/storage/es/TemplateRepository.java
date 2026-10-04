/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import com.scriptagent.writing.common.constants.EsIndexConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import static com.scriptagent.writing.common.constants.EsIndexConstants.INDEX_TEMPLATE;

/**
 * ES 模板库仓库（第 6 节模板引擎，落地第 1 节占位接口）.
 *
 * <p>模板是结构资产，与素材内容资产（{@code writing_material_chunk}）职责分离。所有 API MUST 强制携带 {@code user_id}（H4
 * 不变量①）——查询 / 删除 / 启停都内嵌 user_id 过滤，越权一律按不存在处理（防越权探测）.
 *
 * <p>非向量索引：无 dense_vector mapping，字段见 {@code docs/TechnicalSolution.md §7.1}. ES 失败统一抛受检 {@link
 * IOException}，由上层转业务异常.
 */
@Repository
public class TemplateRepository {

  private static final Logger log = LoggerFactory.getLogger(TemplateRepository.class);

  private static final String STATUS_ENABLED = "enabled";
  private static final String STATUS_DISABLED = "disabled";

  private final RestHighLevelClient client;

  public TemplateRepository(RestHighLevelClient client) {
    this.client = client;
  }

  /** 创建 {@code writing_template} 索引（已存在则跳过）；模板为嵌套 variables + status keyword，无向量. */
  public void ensureIndex() throws IOException {
    boolean exists =
        client.indices().exists(new GetIndexRequest(INDEX_TEMPLATE), RequestOptions.DEFAULT);
    if (exists) {
      log.info("ES 模板索引已存在：{}", INDEX_TEMPLATE);
      return;
    }
    CreateIndexRequest request = new CreateIndexRequest(INDEX_TEMPLATE);
    request.source(mappingJson(), XContentType.JSON);
    client.indices().create(request, RequestOptions.DEFAULT);
    log.info("ES 模板索引已创建：{}", INDEX_TEMPLATE);
  }

  /**
   * 保存模板（新增或覆盖同 id）. 无 id 时自动生成 {@code tpl-} 前缀；写 {@code create_time}/{@code update_time}.
   *
   * @param doc 模板文档（userId 由调用方注入，本层不取 UserContext）
   * @return 落库后的模板 id
   */
  public String save(TemplateDoc doc) throws IOException {
    if (doc.getUserId() == null) {
      throw new IllegalArgumentException("TemplateDoc.userId 不能为空（模板按用户隔离，禁止无主数据）");
    }
    if (doc.getId() == null) {
      doc.setId("tpl-" + UUID.randomUUID());
    }
    long now = System.currentTimeMillis();
    if (doc.getCreateTime() == null) {
      doc.setCreateTime(now);
    }
    doc.setUpdateTime(now);
    IndexRequest request =
        new IndexRequest(INDEX_TEMPLATE).id(doc.getId()).source(toSource(doc), XContentType.JSON);
    // 写后读一致：立即 refresh，避免近实时延迟导致"创建后马上查不到"（全链路联调修复）
    request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    client.index(request, RequestOptions.DEFAULT);
    return doc.getId();
  }

  /** 按 id 取模板并强制归属校验：{@code _id} + {@code user_id} 同时命中才返回（他人模板 → 空，防越权探测）. */
  public Optional<TemplateDoc> findByIdAndUserId(String id, Long userId) throws IOException {
    SearchRequest request = new SearchRequest(INDEX_TEMPLATE);
    SearchSourceBuilder source = new SearchSourceBuilder();
    source.query(
        QueryBuilders.boolQuery()
            .filter(QueryBuilders.termQuery("_id", id))
            .filter(QueryBuilders.termQuery("user_id", userId)));
    source.size(1);
    request.source(source);
    SearchResponse response = client.search(request, RequestOptions.DEFAULT);
    SearchHit[] hits = response.getHits().getHits();
    if (hits.length == 0) {
      return Optional.empty();
    }
    return Optional.of(fromHit(hits[0]));
  }

  /** 列出某用户全部模板（user_id 隔离）. */
  public List<TemplateDoc> findByUserId(Long userId) throws IOException {
    SearchRequest request = new SearchRequest(INDEX_TEMPLATE);
    SearchSourceBuilder source = new SearchSourceBuilder();
    source.query(QueryBuilders.termQuery("user_id", userId));
    source.size(10000);
    request.source(source);
    SearchResponse response = client.search(request, RequestOptions.DEFAULT);
    List<TemplateDoc> result = new ArrayList<>();
    for (SearchHit hit : response.getHits().getHits()) {
      result.add(fromHit(hit));
    }
    return result;
  }

  /**
   * 删除模板：{@code _id} + {@code user_id} 双条件 DeleteByQuery（防 TOCTOU，他人模板不受影响）.
   *
   * @return true=实际删除，false=不存在或非本人（调用方按"不存在/越权统一不存在"处理）
   */
  public boolean delete(String id, Long userId) throws IOException {
    DeleteByQueryRequest request =
        new DeleteByQueryRequest(INDEX_TEMPLATE)
            .setQuery(
                QueryBuilders.boolQuery()
                    .filter(QueryBuilders.termQuery("_id", id))
                    .filter(QueryBuilders.termQuery("user_id", userId)));
    // 删除立即 refresh，保证后续查询（含越权探测判不存在）读到的是一致的
    request.setRefresh(true);
    BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);
    return response.getDeleted() > 0;
  }

  /**
   * 停用模板：{@code _id} + {@code user_id} 双条件 UpdateByQuery 置 {@code status=disabled}，防 TOCTOU.
   *
   * @return true=状态已翻转，false=不存在或非本人
   */
  public boolean disable(String id, Long userId) throws IOException {
    return updateStatus(id, userId, STATUS_DISABLED);
  }

  /**
   * 启用模板：{@code _id} + {@code user_id} 双条件 UpdateByQuery 置 {@code status=enabled}.
   *
   * @return true=状态已翻转，false=不存在或非本人
   */
  public boolean enable(String id, Long userId) throws IOException {
    return updateStatus(id, userId, STATUS_ENABLED);
  }

  /** 模板 ES 源字段 → {@link TemplateDoc}（含 variables 嵌套反序列化）. */
  private static TemplateDoc fromHit(SearchHit hit) {
    TemplateDoc doc = new TemplateDoc();
    doc.setId(hit.getId());
    Map<String, Object> src = hit.getSourceAsMap();
    if (src.get("user_id") instanceof Number n) {
      doc.setUserId(n.longValue());
    }
    doc.setName((String) src.get("name"));
    doc.setDescription((String) src.get("description"));
    doc.setStructure((String) src.get("structure"));
    doc.setPrompt((String) src.get("prompt"));
    doc.setStatus((String) src.get("status"));
    if (src.get("create_time") instanceof Number n) {
      doc.setCreateTime(n.longValue());
    }
    if (src.get("update_time") instanceof Number n) {
      doc.setUpdateTime(n.longValue());
    }
    Object variables = src.get("variables");
    if (variables instanceof List<?> list) {
      List<TemplateVariable> vars = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          vars.add(toVariable(map));
        }
      }
      doc.setVariables(vars);
    }
    return doc;
  }

  /** ES 嵌套 map → {@link TemplateVariable}. */
  @SuppressWarnings("unchecked")
  private static TemplateVariable toVariable(Map<?, ?> map) {
    TemplateVariable v = new TemplateVariable();
    v.setKey(str(map.get("key")));
    v.setLabel(str(map.get("label")));
    v.setType(str(map.get("type")));
    if (map.get("required") instanceof Boolean b) {
      v.setRequired(b);
    }
    v.setPlaceholder(str(map.get("placeholder")));
    Object options = map.get("options");
    if (options instanceof List<?> opts) {
      List<String> vals = new ArrayList<>();
      for (Object o : opts) {
        vals.add(o == null ? null : o.toString());
      }
      v.setOptions(vals);
    }
    return v;
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  /** {@link TemplateDoc} → ES 源字段（variables 转嵌套对象数组）. */
  private static Map<String, Object> toSource(TemplateDoc doc) {
    Map<String, Object> source = new HashMap<>();
    source.put("user_id", doc.getUserId());
    source.put("name", doc.getName());
    source.put("description", doc.getDescription());
    source.put("structure", doc.getStructure());
    source.put("prompt", doc.getPrompt());
    source.put("status", doc.getStatus());
    source.put("create_time", doc.getCreateTime());
    source.put("update_time", doc.getUpdateTime());
    List<Map<String, Object>> vars = new ArrayList<>();
    for (TemplateVariable v : doc.getVariables()) {
      Map<String, Object> vm = new HashMap<>();
      vm.put("key", v.getKey());
      vm.put("label", v.getLabel());
      vm.put("type", v.getType());
      vm.put("required", v.isRequired());
      vm.put("placeholder", v.getPlaceholder());
      vm.put("options", v.getOptions());
      vars.add(vm);
    }
    source.put("variables", vars);
    return source;
  }

  private boolean updateStatus(String id, Long userId, String status) throws IOException {
    UpdateByQueryRequest request = new UpdateByQueryRequest(INDEX_TEMPLATE);
    request.setQuery(
        QueryBuilders.boolQuery()
            .filter(QueryBuilders.termQuery("_id", id))
            .filter(QueryBuilders.termQuery("user_id", userId)));
    Map<String, Object> params = new HashMap<>();
    params.put("newStatus", status);
    params.put("now", System.currentTimeMillis());
    Script script =
        new Script(
            ScriptType.INLINE,
            "painless",
            "ctx._source.status = params.newStatus; ctx._source.update_time = params.now;",
            params);
    request.setScript(script);
    // 状态翻转立即 refresh，保证后续查询读到一致状态
    request.setRefresh(true);
    BulkByScrollResponse response = client.updateByQuery(request, RequestOptions.DEFAULT);
    return response.getUpdated() > 0;
  }

  private String mappingJson() {
    return "{"
        + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},"
        + "\"mappings\":{"
        + "  \"properties\":{"
        + "    \"user_id\":{\"type\":\"long\"},"
        + "    \"name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
        + "    \"description\":{\"type\":\"text\"},"
        + "    \"structure\":{\"type\":\"text\"},"
        + "    \"variables\":{\"type\":\"nested\",\"properties\":{"
        + "      \"key\":{\"type\":\"keyword\"},"
        + "      \"label\":{\"type\":\"text\"},"
        + "      \"type\":{\"type\":\"keyword\"},"
        + "      \"required\":{\"type\":\"boolean\"},"
        + "      \"placeholder\":{\"type\":\"text\"},"
        + "      \"options\":{\"type\":\"keyword\"}"
        + "    }},"
        + "    \"prompt\":{\"type\":\"text\"},"
        + "    \"status\":{\"type\":\"keyword\"},"
        + "    \"create_time\":{\"type\":\"long\"},"
        + "    \"update_time\":{\"type\":\"long\"}"
        + "  }"
        + "}}";
  }

  /** 测试用：返回模板索引名常量以便断言. */
  static String indexName() {
    return EsIndexConstants.INDEX_TEMPLATE;
  }
}

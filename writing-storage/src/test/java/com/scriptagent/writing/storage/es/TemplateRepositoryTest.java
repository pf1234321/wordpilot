/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import com.scriptagent.writing.common.constants.EsIndexConstants;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板库 ES 验收：索引建好（含 status/variables mapping）；模板按 user_id 隔离；启用停用生效；删除只删本人.
 *
 * <p>依赖本地 ES 7.17 服务，标记 {@code @Tag("integration")} CI 跳过、本地人工跑. 启动前确保 {@code elasticsearch.uris}
 * 配置正确（默认 {@code http://127.0.0.1:9200}）.
 */
@SpringBootTest
@Tag("integration")
class TemplateRepositoryTest {

  @Autowired private TemplateRepository repository;
  @Autowired private RestHighLevelClient client;

  @Test
  @DisplayName("启动建 writing_template 索引；status/variables 字段由存取往返证明可用")
  void ensureIndex_createsWritingTemplate() throws Exception {
    repository.ensureIndex();
    assertThat(TemplateRepository.indexName())
        .isEqualTo(EsIndexConstants.INDEX_TEMPLATE)
        .isEqualTo("writing_template");

    // 索引真实存在（GetIndexRequest 探活）
    boolean exists =
        client
            .indices()
            .exists(new GetIndexRequest(EsIndexConstants.INDEX_TEMPLATE), RequestOptions.DEFAULT);
    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("存取往返：save → findByIdAndUserId 读回字段一致，无 id 自动生成 tpl- 前缀")
  void saveAndFind_roundTrip() throws Exception {
    repository.ensureIndex();
    long user = 6001L;
    TemplateDoc doc = weekly("tpl-round-" + user, user);

    String id = repository.save(doc);
    refreshIndex();

    Optional<TemplateDoc> found = repository.findByIdAndUserId(id, user);
    assertThat(found).isPresent();
    TemplateDoc back = found.get();
    assertThat(back.getId()).isEqualTo(id);
    assertThat(back.getName()).isEqualTo(doc.getName());
    assertThat(back.getStructure()).isEqualTo(doc.getStructure());
    assertThat(back.getStatus()).isEqualTo("enabled");
    assertThat(back.getVariables()).hasSize(4);
    assertThat(back.getVariables().get(0).getKey()).isEqualTo("title");

    repository.delete(id, user);
    refreshIndex();
  }

  @Test
  @DisplayName("无 id 入库自动生成 tpl- 前缀")
  void save_generatesIdWhenAbsent() throws Exception {
    repository.ensureIndex();
    TemplateDoc doc = weekly(null, 6002L);

    String id = repository.save(doc);
    refreshIndex();

    assertThat(id).startsWith("tpl-");
    assertThat(repository.findByIdAndUserId(id, 6002L)).isPresent();

    repository.delete(id, 6002L);
    refreshIndex();
  }

  @Test
  @DisplayName("关键回归：模板按 user_id 隔离，他人不可见（越权按不存在）")
  void templates_isolatedByUser_otherUsersInvisible() throws Exception {
    repository.ensureIndex();
    long userA = 6101L;
    long userB = 6102L;
    TemplateDoc a = weekly("tpl-a", userA);

    String idA = repository.save(a);
    refreshIndex();

    // userB 列不出 userA 的模板
    List<TemplateDoc> bList = repository.findByUserId(userB);
    assertThat(bList).noneMatch(t -> t.getId().equals(idA));
    // userB 按 id 查 userA 模板 → 空（防越权探测）
    assertThat(repository.findByIdAndUserId(idA, userB)).isEmpty();
    // userA 本人可查
    assertThat(repository.findByIdAndUserId(idA, userA)).isPresent();

    repository.delete(idA, userA);
    refreshIndex();
  }

  @Test
  @DisplayName("关键回归：启用停用生效——disable 置 disabled，enable 回 enabled")
  void disableTemplate_statusFlipsEnabledToDisabled() throws Exception {
    repository.ensureIndex();
    long user = 6201L;
    String id = repository.save(weekly("tpl-toggle", user));
    refreshIndex();

    assertThat(repository.disable(id, user)).isTrue();
    refreshIndex();
    assertThat(repository.findByIdAndUserId(id, user))
        .map(TemplateDoc::getStatus)
        .hasValue("disabled");

    assertThat(repository.enable(id, user)).isTrue();
    refreshIndex();
    assertThat(repository.findByIdAndUserId(id, user))
        .map(TemplateDoc::getStatus)
        .hasValue("enabled");

    // 他人对该模板启停 → false（越权按不存在，不改动）
    assertThat(repository.disable(id, 6202L)).isFalse();
    refreshIndex();
    assertThat(repository.findByIdAndUserId(id, user))
        .map(TemplateDoc::getStatus)
        .hasValue("enabled");

    repository.delete(id, user);
    refreshIndex();
  }

  @Test
  @DisplayName("删除只删本人模板，他人模板不受影响")
  void delete_removesOnlyOwnTemplate() throws Exception {
    repository.ensureIndex();
    long user = 6301L;
    long other = 6302L;
    String id = repository.save(weekly("tpl-del", user));
    String otherId = repository.save(weekly("tpl-other", other));
    refreshIndex();

    // 他人无法删除该模板
    assertThat(repository.delete(id, other)).isFalse();
    assertThat(repository.findByIdAndUserId(id, user)).isPresent();

    // 本人删除后为空，且不影响他人模板
    assertThat(repository.delete(id, user)).isTrue();
    refreshIndex();
    assertThat(repository.findByIdAndUserId(id, user)).isEmpty();
    assertThat(repository.findByIdAndUserId(otherId, other)).isPresent();

    repository.delete(otherId, other);
    refreshIndex();
  }

  private static TemplateDoc weekly(String id, long userId) {
    TemplateDoc tpl = new TemplateDoc();
    tpl.setId(id);
    tpl.setUserId(userId);
    tpl.setName("周报模板");
    tpl.setDescription("标准周报，用于员工每周向直属上级汇报");
    tpl.setStructure(
        "请按以下结构生成一份周报：\n标题：${title}\n\n"
            + "【本周工作】\n${week_work}\n\n"
            + "【下周计划】\n${next_plan}\n\n"
            + "【风险与求助】\n${risk}\n");
    tpl.setPrompt("生成一份语言简洁、条理清晰、面向直属上级汇报的标准周报。");
    tpl.setStatus("enabled");
    TemplateVariable title = new TemplateVariable();
    title.setKey("title");
    title.setLabel("周报标题");
    title.setType("text");
    title.setRequired(true);
    TemplateVariable week = new TemplateVariable();
    week.setKey("week_work");
    week.setLabel("本周工作");
    week.setType("textarea");
    week.setRequired(true);
    TemplateVariable plan = new TemplateVariable();
    plan.setKey("next_plan");
    plan.setLabel("下周计划");
    plan.setType("textarea");
    plan.setRequired(true);
    TemplateVariable risk = new TemplateVariable();
    risk.setKey("risk");
    risk.setLabel("风险与求助");
    risk.setType("textarea");
    risk.setRequired(false);
    tpl.setVariables(List.of(title, week, plan, risk));
    return tpl;
  }

  private void refreshIndex() throws Exception {
    client
        .indices()
        .refresh(new RefreshRequest(EsIndexConstants.INDEX_TEMPLATE), RequestOptions.DEFAULT);
  }
}

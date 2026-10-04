/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.TemplateUpsertRequest;
import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.es.TemplateDoc;
import com.scriptagent.writing.storage.es.TemplateRepository;
import com.scriptagent.writing.storage.es.TemplateVariable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 模板管理接口验收（全链路联调新增）：创建校验+按用户归属；PATCH 部分更新；删除越权按不存在. */
class TemplateControllerTest {

  private static final long USER_A = 1L;

  private final TemplateRepository templateRepository = mock(TemplateRepository.class);
  private final TemplateController controller = new TemplateController(templateRepository);

  @BeforeEach
  void setUp() {
    UserContext.set(USER_A);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  private static TemplateVariable var(String key) {
    TemplateVariable v = new TemplateVariable();
    v.setKey(key);
    v.setLabel("标题");
    v.setType("text");
    v.setRequired(true);
    return v;
  }

  @Test
  @DisplayName("创建模板：落库并返回模板 id；注入当前 user_id，status 缺省 enabled")
  void create_savesDoc_returnsId() throws Exception {
    TemplateUpsertRequest req =
        new TemplateUpsertRequest(
            "周报", "周报模板", "标题：${title}\n正文：${body}", "规范说明", null, List.of(var("title")));
    when(templateRepository.save(any(TemplateDoc.class)))
        .thenAnswer(
            inv -> {
              TemplateDoc d = inv.getArgument(0);
              d.setId("tpl-1");
              return "tpl-1";
            });

    ApiResponse<String> resp = controller.create(req);

    assertEquals("tpl-1", resp.data());
    ArgumentCaptor<TemplateDoc> captor = ArgumentCaptor.forClass(TemplateDoc.class);
    verify(templateRepository).save(captor.capture());
    TemplateDoc saved = captor.getValue();
    assertEquals(USER_A, saved.getUserId());
    assertEquals("周报", saved.getName());
    assertEquals("enabled", saved.getStatus());
    assertEquals(1, saved.getVariables().size());
  }

  @Test
  @DisplayName("创建模板：名称或结构为空拒绝（不落库）")
  void create_missingStructure_rejects() {
    TemplateUpsertRequest req = new TemplateUpsertRequest("周报", null, null, null, null, List.of());

    assertThrows(IllegalArgumentException.class, () -> controller.create(req));
  }

  @Test
  @DisplayName("模板列表：仅返回当前用户模板")
  void list_returnsUserTemplates() throws Exception {
    TemplateDoc doc = new TemplateDoc();
    doc.setId("tpl-1");
    doc.setName("周报");
    when(templateRepository.findByUserId(USER_A)).thenReturn(List.of(doc));

    ApiResponse<List<TemplateDoc>> resp = controller.list();

    assertEquals(1, resp.data().size());
    assertEquals("周报", resp.data().get(0).getName());
    verify(templateRepository).findByUserId(USER_A);
  }

  @Test
  @DisplayName("更新模板：PATCH 只合并传入字段，保留未传字段")
  void update_mergesProvidedFields() throws Exception {
    TemplateDoc existing = new TemplateDoc();
    existing.setId("tpl-1");
    existing.setName("旧名");
    existing.setStructure("旧结构");
    when(templateRepository.findByIdAndUserId("tpl-1", USER_A)).thenReturn(Optional.of(existing));
    when(templateRepository.save(existing)).thenReturn("tpl-1");

    controller.update("tpl-1", new TemplateUpsertRequest("新名", null, null, null, "disabled", null));

    assertEquals("新名", existing.getName());
    assertEquals("旧结构", existing.getStructure());
    assertEquals("disabled", existing.getStatus());
  }

  @Test
  @DisplayName("更新模板：他人模板/不存在统一按不存在处理")
  void update_notFoundThrows() throws Exception {
    when(templateRepository.findByIdAndUserId("tpl-9", USER_A)).thenReturn(Optional.empty());

    BusinessException e =
        assertThrows(
            BusinessException.class,
            () ->
                controller.update(
                    "tpl-9", new TemplateUpsertRequest("x", null, "s", null, null, null)));

    assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, e.getErrorCode());
  }

  @Test
  @DisplayName("删除模板：成功时返回；不存在/越权按不存在处理")
  void delete_notFoundThrows() throws Exception {
    when(templateRepository.delete("tpl-9", USER_A)).thenReturn(false);

    BusinessException e = assertThrows(BusinessException.class, () -> controller.delete("tpl-9"));

    assertEquals(ErrorCode.TEMPLATE_NOT_FOUND, e.getErrorCode());
  }

  @Test
  @DisplayName("删除模板：本人模板删除成功")
  void delete_removes() throws Exception {
    when(templateRepository.delete("tpl-1", USER_A)).thenReturn(true);

    ApiResponse<Void> resp = controller.delete("tpl-1");

    org.junit.jupiter.api.Assertions.assertEquals(0, resp.code());
    verify(templateRepository).delete("tpl-1", USER_A);
  }
}

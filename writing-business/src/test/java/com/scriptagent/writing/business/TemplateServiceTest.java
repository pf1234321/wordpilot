/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.es.TemplateDoc;
import com.scriptagent.writing.storage.es.TemplateVariable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板渲染验收：必填项缺失报错（防残缺稿）；占位符无残留；Prompt 组装含模板正文 + 规范说明.
 *
 * <p>{@code render} 为纯函数、无状态（不触 ES），直接实例化 {@link TemplateService}.
 */
class TemplateServiceTest {

  private final TemplateService service = new TemplateService();

  @Test
  @DisplayName("关键回归：必填项缺失抛 MISSING_REQUIRED_PARAM（不校验 → 生成残缺稿）")
  void render_missingRequiredParam_throws() {
    // 缺 week_work：params 只给 title
    Map<String, Object> params = Map.of("title", "周报");

    BusinessException e =
        assertThrows(BusinessException.class, () -> service.render(weeklyTemplate(), params));
    assertEquals(ErrorCode.MISSING_REQUIRED_PARAM, e.getErrorCode());
  }

  @Test
  @DisplayName("关键回归：全部占位符被替换，无残留 ${...}")
  void render_replacesAllPlaceholders_noResidue() {
    String rendered = service.render(weeklyTemplate(), fullParams());

    assertFalse(rendered.contains("${"), "渲染后不得残留未替换占位符");
    assertTrue(rendered.contains("标题：市场部张三 2026年第1周周报"));
    assertTrue(rendered.contains("1. 完成新品上线的市场推广方案"));
    assertTrue(rendered.contains("1. 落地推广方案执行"));
    assertTrue(rendered.contains("无"));
  }

  @Test
  @DisplayName("可选变量缺省不抛（required=false 的 risk 不填也渲染；其占位符按契约保留字面量）")
  void render_optionalMissing_doesNotThrow() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("title", "周报");
    params.put("week_work", "本周工作内容");
    params.put("next_plan", "下周计划内容");
    // 缺可选 risk

    String rendered = service.render(weeklyTemplate(), params);

    // 必填项占位符已替换，无残留
    assertFalse(rendered.contains("${week_work}"));
    assertFalse(rendered.contains("${next_plan}"));
    // 未提供的可选变量占位符按契约保留字面量（不 NPE、不吞）
    assertTrue(rendered.contains("${risk}"));
  }

  @Test
  @DisplayName("Prompt 组装：以 prompt 规范说明开头，含填充后的模板正文")
  void render_assemblePrompt_containsStructureAndPrompt() {
    TemplateDoc tpl = weeklyTemplate();
    String rendered = service.render(tpl, fullParams());

    String expectedPrefix = tpl.getPrompt() + "\n\n";
    assertTrue(rendered.startsWith(expectedPrefix), "结果应以 prompt 规范说明 + 空行开头");
    assertTrue(rendered.contains("请按以下结构生成一份周报："), "应含模板正文骨架");
    assertTrue(rendered.contains("标题：市场部张三 2026年第1周周报"), "应含填充后的正文");
  }

  @Test
  @DisplayName("未声明占位符且无参数值：原样保留字面量，不 NPE 不吞")
  void render_nonVariablePlaceholder_remainsLiteral() {
    TemplateDoc tpl = weeklyTemplate();
    // 在 structure 追加一个未在 variables 声明的占位符
    tpl.setStructure(tpl.getStructure() + "\n${undocumented}");

    String rendered = service.render(tpl, fullParams());

    assertTrue(rendered.contains("${undocumented}"), "未声明占位符无参数值时保留字面量");
  }

  private static TemplateDoc weeklyTemplate() {
    TemplateDoc tpl = new TemplateDoc();
    tpl.setId("tpl-weekly-001");
    tpl.setName("周报模板");
    tpl.setDescription("标准周报，用于员工每周向直属上级汇报");
    tpl.setStructure(
        "请按以下结构生成一份周报：\n标题：${title}\n\n"
            + "【本周工作】\n${week_work}\n\n"
            + "【下周计划】\n${next_plan}\n\n"
            + "【风险与求助】\n${risk}\n");
    tpl.setPrompt("生成一份语言简洁、条理清晰、面向直属上级汇报的标准周报。");
    tpl.setStatus("enabled");
    tpl.setVariables(
        List.of(
            var("title", "周报标题", "text", true),
            var("week_work", "本周工作", "textarea", true),
            var("next_plan", "下周计划", "textarea", true),
            var("risk", "风险与求助", "textarea", false)));
    return tpl;
  }

  private static TemplateVariable var(String key, String label, String type, boolean required) {
    TemplateVariable v = new TemplateVariable();
    v.setKey(key);
    v.setLabel(label);
    v.setType(type);
    v.setRequired(required);
    return v;
  }

  private static Map<String, Object> fullParams() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("title", "市场部张三 2026年第1周周报");
    params.put("week_work", "1. 完成新品上线的市场推广方案");
    params.put("next_plan", "1. 落地推广方案执行");
    params.put("risk", "无");
    return params;
  }
}

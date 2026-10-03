/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.es.TemplateDoc;
import com.scriptagent.writing.storage.es.TemplateVariable;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 模板引擎渲染（能力三"固定模板写作"的载体，§7.2）.
 *
 * <p>纯函数、无状态（宪法二）：输入模板 + 用户参数，输出模板写作 Prompt 文本。三步：①校验必填项（variables 中 {@code required=true} 的 key
 * 必须在参数里，防生成残缺稿）②按 {@code ${key}} 替换 structure 占位符（key 与 variables 一一对应）③组装 Prompt（规范说明 +
 * 填充后正文）。不校验 status、不调 LLM、不落稿件——停用拦截与模型调用属 第 8 节写作编排；模板 CRUD 由第 8 节 Controller 直接经 {@code
 * TemplateRepository} 处理，本服务不持有仓库.
 */
@Service
public class TemplateService {

  /**
   * 渲染模板：校验必填 → 替换占位符 → 组装模板写作 Prompt.
   *
   * @param tpl 模板文档（由调用方从 ES 模板库 {@code TemplateRepository} 读取）
   * @param params 用户提交参数（key 与 variables 的 key 一一对应）
   * @return 模板写作 Prompt 文本（prompt 规范说明 + 填充后正文）
   */
  public String render(TemplateDoc tpl, Map<String, Object> params) {
    // 1. 校验必填项：遍历 variables，required=true 的 key 必须在 params 里（防残缺稿）
    for (TemplateVariable v : tpl.getVariables()) {
      if (Boolean.TRUE.equals(v.isRequired()) && !params.containsKey(v.getKey())) {
        throw new BusinessException(ErrorCode.MISSING_REQUIRED_PARAM, v.getKey());
      }
    }

    // 2. 替换 structure 里的 ${变量名}
    String body = tpl.getStructure();
    for (Map.Entry<String, Object> entry : params.entrySet()) {
      body = body.replace("${" + entry.getKey() + "}", entry.getValue().toString());
    }

    // 3. 组装模板写作 Prompt：规范说明 + 填充后正文（身份 Prompt 由写作编排层注入）
    return tpl.getPrompt() + "\n\n" + body;
  }
}

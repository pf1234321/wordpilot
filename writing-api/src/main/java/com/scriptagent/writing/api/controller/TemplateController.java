/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.TemplateUpsertRequest;
import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.es.TemplateDoc;
import com.scriptagent.writing.storage.es.TemplateRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模板管理接口（全链路联调新增）.
 *
 * <p>模板是 ES 结构资产；CRUD 直接经 {@link TemplateRepository}（第 6 节服务设计：模板 CRUD 由 Controller 经仓库处理， {@link
 * com.scriptagent.writing.business.TemplateService} 只负责渲染）。创建时 {@code name}/{@code structure} 必填；
 * PATCH 部分更新；查询 / 删除 / 更新先做 {@code _id} + {@code user_id} 归属校验，越权按不存在处理（防越权探测）。user_id 唯一来源 {@link
 * UserContext#require()}（H4 不变量①）。
 */
@RestController
@RequestMapping("/api/template")
public class TemplateController {

  private final TemplateRepository templateRepository;

  public TemplateController(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  /** 创建模板，返回模板 id（status 缺省 enabled）. */
  @PostMapping
  public ApiResponse<String> create(@RequestBody TemplateUpsertRequest req) {
    if (req.name() == null
        || req.name().isBlank()
        || req.structure() == null
        || req.structure().isBlank()) {
      throw new IllegalArgumentException("模板名称与结构不能为空");
    }
    Long userId = UserContext.require();
    TemplateDoc doc = new TemplateDoc();
    doc.setUserId(userId);
    doc.setName(req.name());
    doc.setDescription(req.description());
    doc.setStructure(req.structure());
    doc.setPrompt(req.prompt());
    doc.setStatus(req.status() == null || req.status().isBlank() ? "enabled" : req.status());
    doc.setVariables(req.variables() == null ? new ArrayList<>() : req.variables());
    try {
      return ApiResponse.success(templateRepository.save(doc));
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
    }
  }

  /** 模板列表（仅当前用户）. */
  @GetMapping
  public ApiResponse<List<TemplateDoc>> list() {
    Long userId = UserContext.require();
    try {
      return ApiResponse.success(templateRepository.findByUserId(userId));
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
    }
  }

  /** 更新模板（PATCH 部分更新；越权按不存在处理）. */
  @PatchMapping("/{id}")
  public ApiResponse<String> update(
      @PathVariable String id, @RequestBody TemplateUpsertRequest req) {
    Long userId = UserContext.require();
    try {
      TemplateDoc existing =
          templateRepository
              .findByIdAndUserId(id, userId)
              .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
      if (req.name() != null) {
        existing.setName(req.name());
      }
      if (req.description() != null) {
        existing.setDescription(req.description());
      }
      if (req.structure() != null) {
        existing.setStructure(req.structure());
      }
      if (req.prompt() != null) {
        existing.setPrompt(req.prompt());
      }
      if (req.status() != null) {
        existing.setStatus(req.status());
      }
      if (req.variables() != null) {
        existing.setVariables(req.variables());
      }
      return ApiResponse.success(templateRepository.save(existing));
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
    }
  }

  /** 删除模板（越权按不存在处理）. */
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id) {
    Long userId = UserContext.require();
    try {
      if (!templateRepository.delete(id, userId)) {
        throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
      }
      return ApiResponse.success();
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
    }
  }
}

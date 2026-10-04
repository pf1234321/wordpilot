/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.MaterialDetail;
import com.scriptagent.writing.api.dto.MaterialListItem;
import com.scriptagent.writing.business.MaterialService;
import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.repository.WritingMaterialRepository;
import java.io.IOException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材管理接口（全链路联调新增）.
 *
 * <p>上传 → {@link MaterialService#upload}（解析 → MySQL 主记录 → 切片 → 向量化 → ES）；列表/详情直接经 {@link
 * WritingMaterialRepository}（user_id 隔离，列表不带正文，详情带正文供预览）；删除经 {@link MaterialService#delete} （先清 ES
 * 再 MySQL 逻辑删除）。user_id 唯一来源 {@link UserContext#require()}（H4 不变量①）；详情/删除越权按不存在处理。
 */
@RestController
@RequestMapping("/api/material")
public class MaterialController {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 100;

  private final MaterialService materialService;
  private final WritingMaterialRepository materialRepository;

  public MaterialController(
      MaterialService materialService, WritingMaterialRepository materialRepository) {
    this.materialService = materialService;
    this.materialRepository = materialRepository;
  }

  /** 上传素材（multipart/form-data），返回素材 id. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Long> upload(@RequestPart("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.PARSE_FAILED, "上传文件为空");
    }
    try {
      Long id = materialService.upload(file.getOriginalFilename(), file.getInputStream());
      return ApiResponse.success(id);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.PARSE_FAILED, e.getMessage());
    }
  }

  /** 素材列表（分页，仅当前用户，create_time 倒序；返回列表视图，不泄漏正文）. */
  @GetMapping
  public ApiResponse<Page<MaterialListItem>> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    Long userId = UserContext.require();
    int bounded = Math.max(1, Math.min(size, MAX_SIZE));
    Page<MaterialListItem> items =
        materialRepository
            .findByUserId(userId, PageRequest.of(page, bounded))
            .map(MaterialListItem::from);
    return ApiResponse.success(items);
  }

  /** 素材详情/预览（含正文；越权按不存在处理）. */
  @GetMapping("/{id}")
  public ApiResponse<MaterialDetail> detail(@PathVariable Long id) {
    Long userId = UserContext.require();
    return ApiResponse.success(
        MaterialDetail.from(
            materialRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATERIAL_NOT_FOUND))));
  }

  /** 删除素材（先清 ES 切片再 MySQL 逻辑删除；越权按不存在处理）. */
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    materialService.delete(id);
    return ApiResponse.success();
  }
}

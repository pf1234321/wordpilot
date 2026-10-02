/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查 / 工程底座冒烟接口.
 *
 * <p>用于验证 writing-start 能起、SpringDoc 能扫到 Controller、ApiResponse 序列化正常.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

  @GetMapping
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.success(
        Map.of(
            "status", "UP",
            "app", "wordpilot",
            "version", "0.1.0-SNAPSHOT",
            "timestamp", Instant.now().toString()));
  }
}

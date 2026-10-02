/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

import java.time.Instant;

/**
 * 统一响应体.
 *
 * @param code 业务状态码；0 表示成功，其他值参考 {@link ResultCode}
 * @param message 人类可读的提示信息
 * @param data 业务数据；可为 null
 * @param timestamp 响应产生时刻（UTC，ISO-8601）
 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

  public static final int CODE_SUCCESS = 0;

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(CODE_SUCCESS, "success", data, Instant.now());
  }

  public static <T> ApiResponse<T> success() {
    return success(null);
  }

  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(code, message, null, Instant.now());
  }

  public static <T> ApiResponse<T> error(ResultCode resultCode) {
    return new ApiResponse<>(resultCode.getCode(), resultCode.getMessage(), null, Instant.now());
  }

  public static <T> ApiResponse<T> error(ResultCode resultCode, String message) {
    return new ApiResponse<>(resultCode.getCode(), message, null, Instant.now());
  }
}

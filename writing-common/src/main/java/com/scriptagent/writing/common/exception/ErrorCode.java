/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.exception;

/**
 * 统一业务错误码（writing-common 级）.
 *
 * <p>由 {@link BusinessException} 承载，供 Service 层在业务失败时抛出，API 层统一转 {@code ApiResponse}。与 writing-api
 * 的 {@code ResultCode}（HTTP 语义）互补：本枚举表达业务 失败原因，不绑定 HTTP 状态码（由 GlobalExceptionHandler 按语义映射）。
 */
public enum ErrorCode {

  /** 登录失败（账号不存在或密码错误统一返回，防账号枚举） */
  LOGIN_FAILED(1001, "账号或密码错误");

  private final int code;
  private final String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}

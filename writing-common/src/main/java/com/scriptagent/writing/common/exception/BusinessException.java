/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.exception;

/**
 * 统一业务异常：Service 层业务失败时抛出，由 API 层 GlobalExceptionHandler 统一转响应.
 *
 * <p>与 {@link ErrorCode} 一一对应；用法：{@code throw new BusinessException(ErrorCode.LOGIN_FAILED)}. 相比直接抛
 * RuntimeException，业务码可被前端精确识别，且日志可定位失败原因.
 */
public class BusinessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String detail) {
    super(errorCode.getMessage() + "：" + detail);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public int getCode() {
    return errorCode.getCode();
  }
}

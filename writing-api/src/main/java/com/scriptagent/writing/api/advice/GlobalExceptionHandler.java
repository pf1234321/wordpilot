/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.advice;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一翻译为 {@link ApiResponse} + 合适的 HTTP 状态码.
 *
 * <p>覆盖 400 / 401 / 403 / 404 / 500，业务异常通过自定义异常 + ResultCode 表达.
 */
@RestControllerAdvice(basePackages = "com.scriptagent.writing")
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Bean 参数校验失败（@Valid / @Validated）→ 400 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));
    log.warn("参数校验失败 path={} detail={}", request.getRequestURI(), detail);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ResultCode.BAD_REQUEST, detail));
  }

  /** 非法参数 → 400 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.warn("非法参数 path={} msg={}", request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ResultCode.BAD_REQUEST, ex.getMessage()));
  }

  /** 未登录 / 鉴权失败 → 401 */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
      UnauthorizedException ex, HttpServletRequest request) {
    log.warn("未登录 path={} msg={}", request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error(ResultCode.UNAUTHORIZED, ex.getMessage()));
  }

  /** 无权限 → 403 */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ApiResponse<Void>> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {
    log.warn("无权限 path={} msg={}", request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error(ResultCode.FORBIDDEN, ex.getMessage()));
  }

  /** 资源不存在 → 404 */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(
      NotFoundException ex, HttpServletRequest request) {
    log.warn("资源不存在 path={} msg={}", request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ResultCode.NOT_FOUND, ex.getMessage()));
  }

  /** 兜底异常 → 500 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex, HttpServletRequest request) {
    log.error("服务异常 path={}", request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ResultCode.INTERNAL_ERROR, "服务器内部错误"));
  }

  private String formatFieldError(FieldError fe) {
    return fe.getField() + ": " + fe.getDefaultMessage();
  }
}

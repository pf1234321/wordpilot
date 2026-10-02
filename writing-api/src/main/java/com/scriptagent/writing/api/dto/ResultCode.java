/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.dto;

/**
 * 业务错误码枚举.
 *
 * <p>HTTP 状态码由 {@code GlobalExceptionHandler} 负责映射，本枚举只定义业务语义码.
 */
public enum ResultCode {
  SUCCESS(0, "success"),

  BAD_REQUEST(400, "请求参数不合法"),
  UNAUTHORIZED(401, "未登录或登录已过期"),
  FORBIDDEN(403, "无访问权限"),
  NOT_FOUND(404, "资源不存在"),

  USER_NOT_FOUND(1001, "用户不存在"),
  BAD_CREDENTIALS(1002, "账号或密码错误"),

  MATERIAL_NOT_FOUND(2001, "素材不存在"),
  MATERIAL_PARSE_FAILED(2002, "素材解析失败"),
  MATERIAL_TYPE_NOT_ALLOWED(2003, "不支持的素材类型"),

  ARTICLE_NOT_FOUND(3001, "稿件不存在"),
  TEMPLATE_NOT_FOUND(4001, "模板不存在"),

  LLM_CALL_FAILED(5001, "LLM 调用失败"),
  EMBEDDING_LOAD_FAILED(5002, "Embedding 模型加载失败"),
  ES_QUERY_FAILED(5003, "ES 检索失败"),

  INTERNAL_ERROR(9999, "服务器内部错误");

  private final int code;
  private final String message;

  ResultCode(int code, String message) {
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

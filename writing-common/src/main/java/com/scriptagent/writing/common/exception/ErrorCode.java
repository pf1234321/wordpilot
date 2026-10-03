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
  LOGIN_FAILED(1001, "账号或密码错误"),

  /** 不支持的文件格式（素材解析仅支持 docx/pdf/txt/md，课件主角一引用） */
  UNSUPPORTED_FORMAT(1002, "不支持的文件格式"),

  /** 文档解析失败（文件损坏或无法读取，不静默产出空文本） */
  PARSE_FAILED(1003, "文档解析失败"),

  /** 素材不存在（含越权访问——统一按不存在处理，防越权探测） */
  MATERIAL_NOT_FOUND(1004, "素材不存在"),

  /** 向量存储操作失败（ES 切片入库 / 清理失败，需整体回退不产生孤儿数据） */
  ES_OPERATION_FAILED(1005, "向量存储操作失败");

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

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
  ES_OPERATION_FAILED(1005, "向量存储操作失败"),

  /** 模板渲染缺少必填参数（variables 中 required=true 的 key 未提交，防生成残缺稿，课件点名） */
  MISSING_REQUIRED_PARAM(1006, "缺少必填参数"),

  /** 模板不存在（含越权访问——统一按不存在处理，防越权探测，第 8 节写作编排） */
  TEMPLATE_NOT_FOUND(1007, "模板不存在"),

  /** 模板已停用（status != enabled 不可用于新写作，历史稿件不受影响，第 8 节写作编排） */
  TEMPLATE_DISABLED(1008, "模板已停用"),

  /** 稿件不存在（含越权访问——详情/重新编辑/导出统一按不存在处理，防越权探测，第 8 节稿件历史） */
  ARTICLE_NOT_FOUND(1009, "稿件不存在"),

  /** 未知写作模式（dialog/rag/template 之外拒绝，不静默降级，第 8 节写作路由） */
  UNKNOWN_WRITE_MODE(1010, "未知写作模式"),

  /** 稿件导出 Word 失败（POI 生成 docx 异常，第 8 节稿件历史） */
  EXPORT_FAILED(1011, "导出失败");

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

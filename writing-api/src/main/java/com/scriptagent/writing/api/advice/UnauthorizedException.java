/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.advice;

/** 未登录或登录已过期. */
public class UnauthorizedException extends RuntimeException {

  public UnauthorizedException(String message) {
    super(message);
  }
}

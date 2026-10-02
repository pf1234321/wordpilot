/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.advice;

/** 资源不存在. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}

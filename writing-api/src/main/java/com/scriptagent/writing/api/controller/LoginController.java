/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.LoginRequest;
import com.scriptagent.writing.business.LoginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口：POST /api/auth/login，账号密码换 Token.
 *
 * <p>由 WebConfig 放行（不经过 TokenInterceptor）；登录失败由 {@code GlobalExceptionHandler} 统一转 {@code
 * BusinessException}(LOGIN_FAILED) → 401.
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

  private final LoginService loginService;

  public LoginController(LoginService loginService) {
    this.loginService = loginService;
  }

  @PostMapping("/login")
  public ApiResponse<String> login(@RequestBody @Valid LoginRequest request) {
    String token = loginService.login(request.username(), request.password());
    return ApiResponse.success(token);
  }
}

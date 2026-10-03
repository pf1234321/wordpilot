/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.encoder.BcryptPasswordEncoder;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.entity.SysUser;
import com.scriptagent.writing.storage.repository.SysUserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 登录业务测试：密码正确发 Token（Redis 带过期）、用户不存在/密码错统一错误. */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @Mock private SysUserRepository userRepository;
  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOps;

  private final BcryptPasswordEncoder encoder = new BcryptPasswordEncoder();
  private LoginService loginService;

  @BeforeEach
  void setUp() {
    loginService = new LoginService(userRepository, redis, encoder);
  }

  /** 只 stub 密码 hash（登录失败路径也会调用 matches → getPassword） */
  private SysUser mockUserWithPassword(String rawPassword) {
    SysUser user = mock(SysUser.class);
    when(user.getPassword()).thenReturn(encoder.encode(rawPassword));
    return user;
  }

  @DisplayName("密码正确发 Token")
  @Test
  void login_withCorrectPassword_returnsTokenAndWritesRedisWithTtl() {
    SysUser user = mockUserWithPassword("secret");
    when(user.getId()).thenReturn(1L);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(redis.opsForValue()).thenReturn(valueOps);

    String token = loginService.login("alice", "secret");

    assertNotNull(token);
    assertFalse(token.isBlank());
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
    assertEquals("token:" + token, keyCaptor.getValue());
    assertEquals("1", valueCaptor.getValue());
    assertEquals(Duration.ofHours(12), ttlCaptor.getValue());
  }

  @DisplayName("用户不存在抛统一错误")
  @Test
  void login_unknownUser_throwsLoginFailed() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    BusinessException ex =
        assertThrows(BusinessException.class, () -> loginService.login("ghost", "any-password"));

    assertEquals(ErrorCode.LOGIN_FAILED, ex.getErrorCode());
  }

  @DisplayName("密码错误与用户不存在返回同一错误（不泄露账号是否存在）")
  @Test
  void login_wrongPassword_throwsSameErrorAsUnknownUser() {
    SysUser user = mockUserWithPassword("right");
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    BusinessException wrongPassword =
        assertThrows(BusinessException.class, () -> loginService.login("alice", "wrong-password"));

    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
    BusinessException unknownUser =
        assertThrows(BusinessException.class, () -> loginService.login("alice", "right"));

    assertEquals(ErrorCode.LOGIN_FAILED, wrongPassword.getErrorCode());
    assertEquals(ErrorCode.LOGIN_FAILED, unknownUser.getErrorCode());
    assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
  }
}

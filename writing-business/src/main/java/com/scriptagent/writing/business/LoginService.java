/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.business;

import com.scriptagent.writing.common.constants.RedisKeys;
import com.scriptagent.writing.common.encoder.BcryptPasswordEncoder;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.entity.SysUser;
import com.scriptagent.writing.storage.repository.SysUserRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录业务编排：查用户 → BCrypt 比对 → 签发 UUID Token 写 Redis → 返回 Token.
 *
 * <p>账号不存在与密码错误统一抛 {@link BusinessException}({@link ErrorCode#LOGIN_FAILED})，
 * 不泄露"该账号是否存在"（防枚举）。Token 只存 Redis（`token:{token}` → userId）， 删 key 即全局登出，不落库。user_id 的后续消费一律经
 * UserContext（防越权）.
 */
@Service
public class LoginService {

  /** Token 有效期 12 小时；如需配置化，改为从 yml 读取（预留扩展点） */
  private static final Duration TOKEN_TTL = Duration.ofHours(12);

  private final SysUserRepository userRepository;
  private final StringRedisTemplate redis;
  private final BcryptPasswordEncoder passwordEncoder;

  public LoginService(
      SysUserRepository userRepository,
      StringRedisTemplate redis,
      BcryptPasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.redis = redis;
    this.passwordEncoder = passwordEncoder;
  }

  public String login(String username, String password) {
    SysUser user = userRepository.findByUsername(username).orElse(null);
    if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
      throw new BusinessException(ErrorCode.LOGIN_FAILED);
    }
    String token = UUID.randomUUID().toString();
    redis.opsForValue().set(RedisKeys.token(token), String.valueOf(user.getId()), TOKEN_TTL);
    return token;
  }
}

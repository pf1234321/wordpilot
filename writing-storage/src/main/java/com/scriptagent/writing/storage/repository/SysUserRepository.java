/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.repository;

import com.scriptagent.writing.storage.entity.SysUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户表数据访问：登录场景按用户名查询唯一用户.
 *
 * <p>提供 {@code findByUsername} 用于登录密码校验（第 3 节登录与鉴权）.
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {
  Optional<SysUser> findByUsername(String username);
}

# Implementation Plan: 登录与鉴权（Login & Auth）

**Branch**: `lesson3-auth` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-login-auth/spec.md`

## Summary

按决策三（极简登录，**不用 SpringSecurity**）实现登录与鉴权：`LoginService`（writing-business）编排登录（按用户名查 `sys_user` → BCrypt 比对 → 签发 UUID Token → Redis 写 `token:{token}`→userId 带过期 → 返回 Token，用户不存在/密码错统一 `BusinessException(ErrorCode.LOGIN_FAILED)` 防枚举）；`TokenInterceptor`（writing-api）全局鉴权：Header Token → Redis 解析 userId → 写 `UserContext`（线程级），请求结束 `clear` 防串号，无/失效 Token → 401；`UserContext`（writing-common）作为全系统 user_id 唯一合法来源（禁止从前端参数取，防越权）。`ErrorCode`+`BusinessException` 新建于 writing-common；`BcryptPasswordEncoder` 注册为 Spring Bean（改造 `PasswordEncoderConfig`）。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)，P3C 门禁 targetJdk=20（代码避免 Java 21-only 语法）

**Primary Dependencies**:
- `writing-storage`（第 1 节）：`SysUser`（entity）、`SysUserRepository.findByUsername` → `Optional<SysUser>`（password 已 BCrypt）
- `writing-common`（第 1 节）：`UserContext`（ThreadLocal set/get/clear）、`BcryptPasswordEncoder.encode/matches`（encoder）、`RedisKeys.TOKEN_PREFIX`/`token(token)`、`PasswordEncoderConfig`（config，空占位待 Bean 化）
- `spring-boot-starter-data-redis`（`RedisTemplate<String,Object>`，writing-storage 已配）
- `spring-web`（writing-api 已有，`HandlerInterceptor`/`WebMvcConfigurer`）
- **无新增第三方依赖**（BCrypt 复用第 1 节自研编码器，Bean 化注入）

**Security 方案（决策三）**:
- 密码：BCrypt 单向加密（第 1 节 `sys_user.password` 已存 BCrypt hash），比对用 `BcryptPasswordEncoder.matches`（防时序攻击）
- Token：`UUID.randomUUID().toString()`，只存 Redis（`token:{token}` → userId），TTL 默认 12h（可配）；删 key 即全局登出，不落库
- 鉴权：`TokenInterceptor`（HandlerInterceptor）+ `UserContext`（ThreadLocal），不用 SpringSecurity

**API / 契约**:
- `POST /api/auth/login`：body `{username, password}` → 成功返回 `{token}`（或 `ApiResponse`），失败统一 `BusinessException(ErrorCode.LOGIN_FAILED)`（经 `GlobalExceptionHandler` 转 JSON）
- 拦截器注册：`WebMvcConfigurer` 注册 `TokenInterceptor`，放行 `/api/auth/login`（及 `/health` 若需），其余 `/**` 一律拦截

**Testing**（harness 先行）:
- `LoginServiceTest`（writing-business，单测）：密码正确发 Token + Redis 写 `token:{token}`=userId 带 TTL；用户不存在/密码错统一 `LOGIN_FAILED`（两种失败错误一致）
- `TokenInterceptorTest`（writing-api，单测）：无 Token → 401；有效 Token preHandle → UserContext.set；失效 Token → 401
- `UserContextTest`（writing-common，单测）：关键回归 `userContext_clearedAfterRequest_preventsLeak`（preHandle 后非空，afterCompletion 后 null）
- `mvn clean verify` 全绿（P3C 组合门禁）

**Constraints**:
- 依赖方向：writing-business → writing-storage + writing-common；writing-api → writing-business + writing-common；writing-common 无下层依赖
- user_id 唯一合法来源 = UserContext，前端参数一律拒绝（H4 不变量①）
- Token 只存 Redis 不落库；登录接口必须放行；其余一律拦截
- 不引入 SpringSecurity / 不建任何表 / 不改第 1 节已定字面量（SysUser/SysUserRepository）
- P3C 门禁 targetJdk=20：写 Java 20 及以下语法

**Scale/Scope**: 单用户 ~200 内网中台；登录低频（秒级），Token 校验每请求一次 Redis 读（可接受）

## Constitution Check

*GATE: Must pass before implementation.*

| 原则 | 状态 | 说明 |
|-------|-----|------|
| 原则一：SpringBoot3.x + Java21 单体 | ✅ | 3 模块联动，单向依赖 |
| 原则二：Agent 无状态，状态外置 | N/A | 本节不涉 Agent |
| 原则三：AgentScope 使用边界；模型统一出口 | N/A | 本节不涉模型 |
| 原则四：极简登录，不用 SpringSecurity | ✅ | **本节即决策三落点**：拦截器 + UserContext，无 SecurityConfig/过滤器链 |
| 原则五：查询强制携带 user_id | ✅ | **本节建立 UserContext 为唯一来源**，禁止前端参数取（防越权） |
| 原则六：JPA 落地规范 | ✅ | 复用第 1 节 `SysUserRepository`，零手写 SQL |
| 原则七：RAG 管线自实现 | N/A | 本节不涉 RAG |
| 原则八：记忆三层分层 | N/A | 本节不涉记忆 |
| 原则九：三类写作共享 Agent 底座 | N/A | 本节不涉写作 |
| 原则十：每个 user story 可演示 | ✅ | 3 个 harness 测试类全绿 |

## Project Structure

### Documentation (this feature)

```text
specs/003-login-auth/
├── plan.md              # 本文件
├── spec.md              # 需求规格
└── tasks.md             # /speckit-tasks 输出
```

### Source Code (repository root)

```text
writing-common/
└── src/main/java/com/scriptagent/writing/common/
    ├── exception/ErrorCode.java          # 业务错误码枚举（含 LOGIN_FAILED）
    ├── exception/BusinessException.java  # 统一业务异常
    └── config/PasswordEncoderConfig.java # 改造：注册 BcryptPasswordEncoder 为 Bean

writing-business/
└── src/main/java/com/scriptagent/writing/business/
    └── LoginService.java                 # 登录业务编排

writing-api/
└── src/main/java/com/scriptagent/writing/api/
    ├── controller/LoginController.java   # POST /api/auth/login
    ├── interceptor/TokenInterceptor.java # 全局鉴权拦截器
    └── config/WebConfig.java             # 注册拦截器，放行 /api/auth/login
```

### Tests

```text
writing-common/src/test/.../UserContextTest.java        # clear 防串号（关键回归）
writing-business/src/test/.../LoginServiceTest.java     # 登录发 Token / 统一错误 / Redis 带过期
writing-api/src/test/.../TokenInterceptorTest.java      # 401 / 写 UserContext / 失效 401
```

**Structure Decision**: 登录（business）+ 鉴权（api）+ 用户上下文（common）三模块分工，贴合技能落位表；不改第 1 节 `SysUser`/`SysUserRepository` 字面量。

## Complexity Tracking

> 需确认点：`BcryptPasswordEncoder`（第 1 节自研）为静态工具类，Bean 化时需提供可实例化的构造函数（或包一层 Bean 方法调用静态方法）；LoginService 注入 `BcryptPasswordEncoder` 具体类（非 Spring Security 的 `PasswordEncoder` 接口）。无 Constitution 违例、无新增第三方依赖。

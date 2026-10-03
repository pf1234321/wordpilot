# Feature Specification: 登录与鉴权（Login & Auth）

**Feature Branch**: `lesson3-auth`
**Created**: 2026-10-03
**Status**: Draft
**Input**: User description: 第3节需求：登录与鉴权——BCrypt + UUID Token + Redis，不用 SpringSecurity（决策三）。落在极简登录决策上，是用户数据完全隔离（素材/会话/稿件/模板按 user_id）的第一道门。

## 用户场景与测试

### User Story 1 — 登录签发 Token：账号密码进系统，拿到 Token（P1）

用户用账号密码登录：按用户名查 MySQL `sys_user`，BCrypt 比对密码，校验通过生成 UUID Token 写入 Redis（`token:{token}` → userId，带过期时间，默认 12h 可配），返回 Token 给前端。用户不存在与密码错误返回**同一个**错误（防账号枚举），不泄露"该账号是否存在"。

**Why this priority**：Token 是整个系统 user_id 隔离的唯一凭证来源；防枚举是安全底线；Token 只存 Redis（不落库）才能服务重启 / 删 key 即全局失效。

**Independent Test**：`LoginService.login(username, password)` 密码正确返回非空 Token 且 Redis 里 `token:{token}` = userId（带 TTL）；用户不存在 / 密码错均抛 `BusinessException(ErrorCode.LOGIN_FAILED)`。

**Acceptance Scenarios**：

1. **Given** 存在 `sys_user` 用户且密码 BCrypt 正确，**When** `login(username, password)`，**Then** 返回非空 UUID Token，Redis `token:{token}` 值为 userId 且带过期时间（默认 12h）。
2. **Given** 用户名不存在 或 密码错误，**When** `login(...)`，**Then** 抛 `BusinessException(ErrorCode.LOGIN_FAILED)`，两种失败错误完全一致（不泄露账号是否存在）。

---

### User Story 2 — 鉴权写 UserContext：每请求带 Token，校验通过写入当前用户（P1）

前端每个业务请求 Header 携带 Token；`TokenInterceptor` 从 Redis 解析 `token:{token}` → userId，写入 `UserContext`（线程级 ThreadLocal），请求结束 `afterCompletion` 必须 `clear`（防线程复用串号）。无 Token / Token 已失效 → 返回 401，拦截请求。登录接口 `/api/auth/login` 放行，其余一律拦截。

**Why this priority**：拦截器 + UserContext 是最简鉴权（决策三，不用 SpringSecurity）；`clear` 不写会串号，是"最阴险的一类 bug"，必须用测试锁死。

**Independent Test**：带有效 Token 的请求 preHandle 返回 true 且 `UserContext.get()` 为对应 userId；无 Token / 失效 Token 返回 false 且响应 401；afterCompletion 后 `UserContext.get()` 为 null。

**Acceptance Scenarios**：

1. **Given** 请求带有效 Token，**When** `preHandle`，**Then** 返回 true 且 `UserContext.get()` = userId。
2. **Given** 请求无 Token 或 Token 已失效（Redis 无此 key），**When** `preHandle`，**Then** 返回 false 且响应 status=401。
3. **Given** 一次带 Token 的请求处理完成，**When** `afterCompletion`，**Then** `UserContext.get()` 为 null（关键回归：防串号）。

---

### User Story 3 — user_id 只来自 UserContext，前端参数一律拒绝（P1）

所有业务查询 / 检索的 `user_id` 唯一合法来源是拦截器写入的 `UserContext`；从前端请求参数取 `user_id` 一律拒绝（防越权）。这是"用户数据完全隔离"的最要命一条。

**Why this priority**：H4 全局不变量①（所有业务查询强制 user_id，来源 UserContext，禁止从前端参数取）。本节建立 UserContext 作为唯一来源，后续各节（4/5/6/8）所有 Service 查询据此取用户。

**Independent Test**：`UserContext` 提供 `set/get/clear`（必要时 `require`/`peek`），grep 确认 Service 层 user_id 来源为 UserContext。

**Acceptance Scenarios**：

1. **Given** 拦截器已写入 UserContext，**When** 业务层取 `UserContext.get()`，**Then** 得到当前登录 userId。
2. **Given** 前端请求参数含 `userId`，**When** 业务处理，**Then** 该参数被忽略（user_id 只认 UserContext，不认请求参数）。

---

### Edge Cases

- **用户不存在 vs 密码错误**：返回完全一致的错误（`LOGIN_FAILED`），不泄露账号是否存在。
- **Token 未携带 / 已失效 / 被删**：拦截器统一返回 401；删 Redis key 即全局登出（无需改代码）。
- **线程复用串号**：`afterCompletion` 必须 `clear`；用关键回归测试锁死。
- **拦截器放行漏配**：登录接口 `/api/auth/login` 必须放行，其余一律拦截（防登录接口被拦死）。
- **密码明文**：存储必须 BCrypt（第 1 节 `SysUser.password` 已 BCrypt），比对用 `matches`（防时序攻击）。

## 功能需求（FR）

- **FR1**：`LoginService`（writing-business）编排登录：`findByUsername` 查用户 → BCrypt `matches` 比对 → 签发 UUID Token → Redis 写 `token:{token}`→userId（带 TTL）→ 返回 Token；用户不存在 / 密码错统一抛 `BusinessException(ErrorCode.LOGIN_FAILED)`。
- **FR2**：`TokenInterceptor`（writing-api，`HandlerInterceptor`）`preHandle` 校验 Header Token：Redis 解析 userId → `UserContext.set`；无 / 失效 → 401 拦截；`afterCompletion` 必须 `UserContext.clear`。
- **FR3**：`LoginController`（writing-api/controller）暴露 `POST /api/auth/login`（username/password → token），登录接口放行。
- **FR4**：`UserContext`（writing-common）线程级 ThreadLocal：`set/get/clear`（+ `require`/`peek`），作为全系统 user_id 唯一合法来源。
- **FR5**：`ErrorCode` + `BusinessException`（writing-common）承载统一业务错误（含 `LOGIN_FAILED`）。
- **FR6**：`BcryptPasswordEncoder` 注册为 Spring Bean（改造 `PasswordEncoderConfig`），`LoginService` 经 Bean 注入使用。

## 明确不做（边界）

- **不做** SpringSecurity（决策三：拦截器 + UserContext 最简方案；SSO/OAuth2 属扩展阶段）。
- **不做** Token 落库 / 持久化（只存 Redis，删 key 即全局失效）。
- **不做** 从前端请求参数取 user_id（一律只认 UserContext，防越权）。
- **不做** 登录接口之外的接口放行（其余一律拦截，防漏配）。
- **不做** 多端 / Refresh Token / 记住我 / 注册 / 找回密码（后续节或扩展阶段）。
- **不做** 明文密码 / 非 BCrypt 存储（第 1 节已 BCrypt，本节沿用）。

## 验收标准（harness）

测试策略按课件"验收 harness"执行，测试类清单与关键回归：

| 测试类 | 覆盖点 | 关键回归 |
|---|---|---|
| `LoginServiceTest` | 密码正确发 Token；用户不存在/密码错统一错误；Token 写 Redis 带过期 | login 成功 → Redis `token:{token}`=userId + TTL；两种失败同一 `LOGIN_FAILED` |
| `TokenInterceptorTest` | 无 Token 401；Token 有效写入 UserContext；Token 失效 401 | 有效 Token preHandle → UserContext.set；无/失效 → 401 |
| `UserContextTest` | 请求结束 clear，防串号 | `userContext_clearedAfterRequest_preventsLeak`（preHandle 后非空，afterCompletion 后 null） |

单测默认跑，实现完成的定义是 `mvn clean verify` 全绿（P3C 组合门禁）。

## 依赖与假设

- **前序交付物**（第 1 节，已存在）：`SysUser`（writing-storage/entity）、`SysUserRepository.findByUsername`（Optional<SysUser>，writing-storage/repository）、`UserContext`（writing-common，已实现 ThreadLocal set/get/clear）、`BcryptPasswordEncoder.encode/matches`（writing-common/encoder，静态方法）、`RedisKeys.TOKEN_PREFIX`/`token(token)`（writing-common/constants）、`PasswordEncoderConfig`（writing-common/config，空占位 @Configuration 待 Bean 化）。
- **新建**（writing-common）：`ErrorCode` 枚举 + `BusinessException`。
- **改造**：`PasswordEncoderConfig` 注册 `BcryptPasswordEncoder` 为 Bean。
- **复用**（writing-api 已有）：`UnauthorizedException`/`ResultCode`/`GlobalExceptionHandler`/`ApiResponse`/`HealthController`。
- **外部依赖**：无新增（BCrypt 用第 1 节自研编码器或 spring-security-crypto 已有传递依赖，Redis 用 writing-storage 已配置的 RedisTemplate）。

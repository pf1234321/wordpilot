# 第 3 节 登录与鉴权 — 任务清单（/speckit-tasks）

**Branch**: `lesson3-auth` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

实现完成定义：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合门禁）。

## 前置（已就绪）

- 分支 `lesson3-auth` 已建（基于 `lesson2-model-service` = 75c137f，干净基线）
- 课件 / TechnicalSolution §3 + 流程一 已读；依赖存在性已核对（见 spec.md 依赖节）

## 任务清单

| # | 模块 | 任务 | 验证 |
|---|---|---|---|
| T001 | writing-common | 新建 `exception/ErrorCode.java`：业务错误码枚举，含 `LOGIN_FAILED`（本节所需） | 编译通过 |
| T002 | writing-common | 新建 `exception/BusinessException.java`：`BusinessException(ErrorCode)` 承载统一业务错误 | 编译通过 |
| T003 | writing-common | 改造 `config/PasswordEncoderConfig.java`：把 `BcryptPasswordEncoder` 注册为 Spring Bean（`@Bean`） | 编译通过 |
| T004 | writing-common | 新建 `UserContextTest.java`（harness，UserContext 第 1 节已存在，补测试）：set/get/clear 行为 + 关键回归 `userContext_clearedAfterRequest_preventsLeak`（请求结束 clear 防串号） | 单测绿 |
| T005 | writing-business | 新建 `LoginService.java`：`login(username,password)` —— `SysUserRepository.findByUsername` 查用户 → `BcryptPasswordEncoder.matches` 比对 → 用户不存在/密码错统一抛 `BusinessException(ErrorCode.LOGIN_FAILED)` → 生成 UUID Token → Redis 写 `RedisKeys.token(token)`→userId 带 TTL（默认 12h，可配）→ 返回 Token | 编译通过 |
| T006 | writing-business | 新建 `LoginServiceTest.java`（harness）：①密码正确发 Token（Redis 有 `token:{token}`=userId 且带 TTL）②用户不存在/密码错均抛 `LOGIN_FAILED`（两种失败错误一致） | 单测绿 |
| T007 | writing-api | 新建 `interceptor/TokenInterceptor.java`（`HandlerInterceptor`）：`preHandle` 读 Header Token → Redis `RedisKeys.token(token)` 解析 userId → `UserContext.set`；无/失效 → 401 返回 false；`afterCompletion` 必须 `UserContext.clear` | 编译通过 |
| T008 | writing-api | 新建 `controller/LoginController.java`：`POST /api/auth/login`（username/password → token，复用 `ApiResponse`/`GlobalExceptionHandler`）+ `config/WebConfig.java`（`WebMvcConfigurer` 注册 `TokenInterceptor`，放行 `/api/auth/login`，其余 `/**` 拦截） | 编译通过 |
| T009 | writing-api | 新建 `TokenInterceptorTest.java`（harness）：①无 Token → 401 ②有效 Token preHandle → `UserContext.set` ③失效 Token → 401（含关键回归：afterCompletion 后 UserContext 清空） | 单测绿 |
| T010 | 全部 | 硬门禁：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合，8 模块 SUCCESS，前序节回归绿） | 全量 verify SUCCESS |
| T011 | specs | 验收报告 `specs/003-login-auth/acceptance.md`（技能第 7 步六项证据 DoD + 剩余人工项） | 报告完成 |

## 软停点：任务清单 ↔ 课件"本节交付物"自动比对

| 课件交付物 | 落地 | 说明 |
|---|---|---|
| `LoginService`（writing-business） | ✅ T005 | 编排登录 |
| `UserRepository`（writing-storage 查 sys_user） | ✅ 沿用 `SysUserRepository`（第 1 节，用户已决策） | 非课件字面量 `UserRepository`，不改第 1 节 |
| `TokenInterceptor`（writing-api） | ✅ T007 | 全局鉴权 |
| `UserContext`（writing-common） | ✅ 第 1 节已存在，本节补测试 T004 | 线程级用户上下文 |
| `UserContextHolder`（writing-common ThreadLocal 存取） | ✅ 并入 `UserContext`（第 1 节已实现 ThreadLocal），不另建独立类 | 课件拆两个类，本工程已合并 |
| harness：`LoginServiceTest` | ✅ T006 | 密码对发 Token / 统一错误 / Redis 带过期 |
| harness：`TokenInterceptorTest` | ✅ T009 | 无 Token 401 / 有效写 UserContext / 失效 401 |
| harness：`UserContextTest` | ✅ T004 | 关键回归 `userContext_clearedAfterRequest_preventsLeak` |
| （补充）`ErrorCode`/`BusinessException` | ✅ T001/T002 | 用户已决策新建 writing-common |
| （补充）`PasswordEncoderConfig` Bean 化 | ✅ T003 | 用户已决策 BCrypt 注册 Bean |
| （补充）`LoginController` + 拦截器注册 | ✅ T008 | TechnicalSolution 流程一明确 `POST /api/auth/login` |

**比对结论**：任务清单与课件交付物**一致**（不缺），新增项均为用户已确认决策（BusinessException/ErrorCode/BCrypt Bean/LoginController）。测试（T004/T006/T009）先行/伴随实现（T005/T007）。无文档外新增对外概念、无已定字面量改动。

# 第 3 节验收报告：登录与鉴权（Login & Auth）—— 六项证据 DoD

* 分支：`lesson3-auth`（基提交 `lesson2-model-service` = 75c137f，第 2 节已先提交，基线干净）
* 验收时间：2026-10-03
* 执行方式：`wordpilot-lesson-dev` 全程流程（H0→specify→clarify→plan→tasks 停点→implement→六项证据 DoD）

## 第 1 项证据：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

根目录执行 `BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify`（含真实 BGE 推理），**BUILD SUCCESS**，8 个模块全部 SUCCESS，总时长 ~47s：

```
[INFO] writing-common ..................................... SUCCESS [  5.5 s]
[INFO] writing-model ...................................... SUCCESS [ 20.2 s]   ← 前序节（含真实模型推理）
[INFO] writing-storage .................................... SUCCESS [  7.0 s]
[INFO] writing-agent-core ................................. SUCCESS [  1.5 s]
[INFO] writing-business ................................... SUCCESS [  3.6 s]   ← 本节 LoginService
[INFO] writing-api ........................................ SUCCESS [  3.8 s]   ← 本节 TokenInterceptor/LoginController
[INFO] writing-start ...................................... SUCCESS [  2.8 s]
[INFO] BUILD SUCCESS
```

本节新增测试全部全绿：`UserContextTest` **5/5**、`LoginServiceTest` **3/3**、`TokenInterceptorTest` **4/4**（0 fail / 0 error / 0 skipped）；前序节回归全绿（writing-model 14/14 含真实 BGE 推理、writing-storage 4/4）。

> 注：spotbugs 曾报 `SERVLET_HEADER`（TokenInterceptor 读 Authorization 头）——鉴权设计行为、值仅作 Redis key 查询（前缀 `token:` 隔离、无注入路径），已按第 2 节惯例加入 `spotbugs-exclude.xml`（逐项注明理由）；其余门禁（P3C PMD / FindSecBugs / checkstyle / spotless）全部通过，无妥协。

## 第 2 项证据：课件 harness 映射表每个测试类存在且非空，关键回归逐个对号

| 测试类（课件） | 落地路径 | 覆盖点 | 结果 |
|---|---|---|---|
| `LoginServiceTest` | writing-business/.../LoginServiceTest.java | 密码正确发 Token；用户不存在/密码错统一错误（防枚举）；Token 写 Redis 带过期 | ✅ 3/3 |
| `TokenInterceptorTest` | writing-api/.../interceptor/TokenInterceptorTest.java | 无 Token 401；Token 有效写入 UserContext；Token 失效 401 | ✅ 4/4 |
| `UserContextTest` | writing-common/.../UserContextTest.java | 上下文 set/get/clear 生命周期 + **关键回归 `userContext_clearedAfterRequest_preventsLeak`**（@DisplayName 课件原文"请求结束上下文被清空，不复用串号"） | ✅ 5/5 |

关键回归对号（断言逐条保真）：
- `userContext_clearedAfterRequest_preventsLeak`（UserContextTest，ThreadLocal 语义：set 后非空 → clear 后 null）+ 同方法名在 TokenInterceptorTest（真实 interceptor：preHandle 写入 → afterCompletion 清空），双保险锁死"防串号"。
- LoginServiceTest：`login_withCorrectPassword_returnsTokenAndWritesRedisWithTtl`（ArgumentCaptor 断言 key=`token:{token}`、value=userId、TTL=12h）；`login_wrongPassword_throwsSameErrorAsUnknownUser`（两种失败 message 全等，不泄露账号是否存在）。

## 第 3 项证据：本节交付物逐项存在性核对

| 交付物（课件 §3 / 用户决策） | 路径 | 存在 |
|---|---|---|
| `LoginService`（writing-business） | writing-business/.../LoginService.java | ✅ |
| `TokenInterceptor`（writing-api） | writing-api/.../interceptor/TokenInterceptor.java | ✅ |
| `UserContext`（writing-common，第 1 节已交付，本节补测试） | writing-common/.../UserContext.java | ✅ |
| `ErrorCode` + `BusinessException`（用户决策，新建 writing-common） | writing-common/.../exception/ | ✅ |
| `PasswordEncoderConfig` Bean 化（用户决策，改造第 1 节占位） | writing-common/.../config/PasswordEncoderConfig.java | ✅ |
| `LoginController` + `WebConfig`（TechnicalSolution 流程一 `POST /api/auth/login`） | writing-api/.../controller + config | ✅ |
| `GlobalExceptionHandler` 扩展（BusinessException → 401/400） | writing-api/.../advice/GlobalExceptionHandler.java | ✅ |
| harness 三个测试类 | 见第 2 项证据 | ✅ |
| 规格产物 | specs/003-login-auth/{spec,plan,tasks,acceptance}.md | ✅ |

## 第 4 项证据：前序节全部测试回归绿（跨节契约证据）

`mvn clean verify` 全量 reactor 通过：writing-common（UserContextTest 5/5）、writing-model（14/14 含真实 BGE bge-m3 ONNX 推理 15.7s）、writing-storage（4/4，AuditTest/ArticleRepositoryTest/WritingMemoryRepositoryTest）全绿。第 1/2 节交付物（`SysUser`/`SysUserRepository`/`ModelService` 等）编译与测试通过；跨节契约点：`SysUserRepository.findByUsername`（第 1 节）被 `LoginService` 消费、`RedisKeys.token`/`UserContext`/`BcryptPasswordEncoder`（第 1 节）被第 3 节复用，均回归验证。

## 第 5 项证据：H4 六条全局不变量逐条自查

| # | 不变量 | 自查结果 |
|---|---|---|
| ① | 所有业务查询/检索强制 user_id（来源 UserContext，前端参数取一律拒绝） | ✅ 本节建立来源链：TokenInterceptor 写 UserContext（T008）；LoginRequest 无 userId 字段，前端传 `userId` 参数被 Jackson 忽略（不绑定）；后续 Service 查询一律 `UserContext.require()` |
| ② | 逻辑删除 `@SQLDelete`+`@Where`、审计 `@CreatedDate` 生效 | 前序节产物，回归绿（AuditTest 3.1s 通过） |
| ③ | grep 无明文 key/密码/模型路径（全 `${ENV_VAR}`） | ✅ 登录不落任何明文：密码输入即时 BCrypt 比对（第 1 节 `sys_user.password` 存 hash）；Token 只存 Redis；grep 无明文凭证 |
| ④ | Embedding 恒 1024 维、ES 索引 dim=1024 + cosine | 前序节契约，回归绿（IndexDimensionTest 2/2） |
| ⑤ | Agent 无状态、状态外置 | 第 5 节范畴，不适用 |
| ⑥ | AgentCore 不直连大模型，统一经 writing-model 出口 | 前序节门面，回归绿 |

## 第 6 项证据：剩余人工项清单（harness 已判卷，以下等你人工过）

1. **真实登录冒烟**：本地起 writing-start（MySQL/Redis 就绪、`sys_user` 有真实用户），`POST /api/auth/login` 正确账号密码 → 拿到 Token；错误密码 → 401 且提示"账号或密码错误"。
2. **Token 失效验证**：Redis `DEL token:{token}` 后，带该 Token 请求业务接口 → 立即 401（删 key 即全局登出）。
3. **user_id 隔离人工项**：带有效 Token 请求业务接口时，前端故意传 `userId=999`，确认被忽略（接口只认 UserContext）。
4. **接口放行复核**：`/api/auth/login` 不带 Token 可访问（200/401 由登录本身决定），其他 `/api/**`（如未来素材接口）无 Token 一律 401。

## 结论

六项证据 DoD 全部满足：全量 `mvn clean verify` 全绿（P3C 组合门禁）、harness 三测试类存在且关键回归逐个对号（含 `userContext_clearedAfterRequest_preventsLeak`）、本节交付物逐项存在、前序节回归绿、H4 不变量①③相关项通过、剩余人工项清单已列明。**第 3 节登录与鉴权验收通过。**

> 变更总结（给 reviewer 的导读）直接输出在对话中，含改动点、重点 review 清单、如何验证三段。

# 第 7 节 前端工作台（mock 支线）— 验收报告

**Branch**: `lesson7-frontend` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Tasks**: [tasks.md](./tasks.md)
**日期**: 2026-10-04

## 结论

**本节完成**：前端工作台 mock 支线已实现并通过全部硬门禁（`vite build` 通过 + vitest 19/19 全绿）。

---

## 六项证据 DoD

### 1. 硬门禁（前端课：`vite build` 通过 + 冒烟全绿）✅

```text
vite v5.4.21 building for production...      → ✓ built in 2.51s（1625 modules transformed，dist/ 产出）
Test Files  6 passed (6)
     Tests  19 passed (19)
```

- `cd frontend && npm run build` → SUCCESS，产物 `frontend/dist/`。
- `cd frontend && npm run test`（vitest run）→ 6 个测试文件 19 条全绿。
- 前端课替代 `mvn clean verify`（技能第 7 步），不运行 Maven 构建。

### 2. 课件 harness 映射表的每个测试类存在且非空，关键回归逐个对号 ✅

| 课件 harness 关键回归 | 落地测试 | 状态 |
|---|---|---|
| `routerGuard_blocksUnauthenticated_redirectsToLogin` | `src/__tests__/routerGuard.test.js` | ✅ 未登录访问 `/workbench` → 重定向 `/login` |
| `tokenExpired_anyRequest401_redirectsToLogin` | `src/__tests__/httpInterceptor.test.js` | ✅ 401 → 清 Token → 跳登录（走真实拦截器链） |
| `sseStream_appendsIncrementally_notFullReplace` | `src/__tests__/sseStream.test.js` | ✅ `content += chunk` 增量追加，非整段替换 |

辅助冒烟（全部非空、全绿）：`login.test.js`（登录拿 Token）、`workbench.test.js`（三大 Tab 各走通一次）、`manage.test.js`（素材/模板/稿件 CRUD + 仅当前用户隔离）。

### 3. "本节交付物"逐项存在性核对 ✅

课件模块分工表每项均落地（见 `tasks.md` 比对表）：`router/index.js`、`api/http.js`、`stores/user.js`、`stores/workbench.js`、`views/Login.vue`、`views/Workbench.vue`、`components/SseStream.vue` + 素材/模板/稿件管理页。存在性核对（15 项）全部 OK，详见终端 `ls` 复核。

### 4. 前序节全部测试回归绿（跨节契约证据）✅

本节为**前端独立工程**（`frontend/`），不进 Maven 构建，**零触碰前序 Java 文件**（git status 未见任何 writing-* 源文件改动）。前端 mock 契约对齐第 3 节 `POST /api/auth/login`（Token 换取/携带/401 语义）、第 8 节写作端点路径（`/api/writing/dialog|rag|template`，mock 先行预埋）。前序节后端测试回归不因本节受影响（无代码交集）。

### 5. H4 六条全局不变量自查 ✅

| 不变量 | 检查 | 结果 |
|---|---|---|
| ① 所有业务查询/检索强制 user_id | 前端 mock 数据层按 userId 键隔离（`db.js` 的 materials/templates/articles 均以 userId 为 key）；后端强制 user_id 由前序节承载 | ✅ |
| ② 逻辑删除/审计 | 前端不涉及（后端前序节） | 不涉及 |
| ③ 无明文 key/密码/模型路径 | grep 无 DashScope key / 模型路径 / 后端地址硬编码；仅 mock 演示密码 `123456`（db.js 演示账号 + login 提示，属 mock 支线） | ✅ |
| ④ Embedding 恒 1024 维 | 前端不涉及（后端前序节） | 不涉及 |
| ⑤ Agent 无状态、状态外置 | 前端仅消费 SSE，不涉及 Agent | 不涉及 |
| ⑥ AgentCore 不直连大模型 | 前端不涉及 | 不涉及 |

### 6. 验收报告收尾：剩余人工项清单 ✅

以下 mock 支线**不覆盖**，由真实后端联调阶段人工验证：

1. **真模型 / 真中间件联调**：真实 `writing-api` 端点（`POST /api/writing/dialog|rag|template`）、真实 SSE（EventSource/fetch stream）、Redis 断线续传、素材真实上传解析切片向量化入库——mock 支线未做。
2. **人工演示**（前端已可用，需你浏览器确认）：`cd frontend && npm run dev` → ①全新浏览器未登录访问工作台 → 跳登录页；②登录后三大 Tab 各写一篇，SSE 实时吐字无卡顿；③素材/模板/稿件管理全流程可操作，仅当前用户数据。
3. **真实后端替换路径**：把 `src/mock/mockApi.js` 各函数改为调用 `src/api/http.js` 真实 axios（契约已对齐）；Axios 拦截器已统一 Token + 401，业务页面无需改动。

---

## 变更总结（给 reviewer 的导读）

以 `git status --short` 实测为准（本节在 `lesson7-frontend` 分支，未 commit，未提交的第 5 节改动随分支带入）。

### 改动点

**新增（前端全新独立工程 `frontend/`，与 Maven 模块平级）**：

| 文件 | 动机 |
|---|---|
| `frontend/package.json` / `vite.config.js` / `index.html` / `.gitignore` / `README.md` | 前端工程底座 + 运行说明 |
| `frontend/src/main.js` / `App.vue` | 应用启动（Pinia + Router + Element Plus） |
| `frontend/src/router/index.js` | 路由 + 守卫（未登录拦截，课件配角逐字保真，守卫抽为 `authGuard` 便于测试） |
| `frontend/src/api/http.js` | Axios 封装：统一 Token + 401 跳登录（401 处理抽为 `handleUnauthorized` 便于测试） |
| `frontend/src/stores/user.js` / `workbench.js` | Pinia 用户状态 + 工作台三 Tab 状态统一管理 |
| `frontend/src/mock/db.js` / `sse.js` / `mockApi.js` | **支线核心**：mock 数据源（按 userId 隔离）+ 定时分片模拟 SSE + mock 后端端点 |
| `frontend/src/views/Login.vue` / `Workbench.vue` | 登录页 + 三大 Tab 工作台（含模板动态表单） |
| `frontend/src/views/MaterialManage.vue` / `TemplateManage.vue` / `ArticleHistory.vue` | 素材 / 模板 / 稿件管理 |
| `frontend/src/components/SseStream.vue` | SSE 流式增量渲染（`content += chunk`） |
| `frontend/src/__tests__/*.test.js`（6 个） | 冒烟 harness（三条关键回归 + 登录/工作台/管理） |

**修改**：无（前端零触碰前序 Java 文件；`specs/` 新增 007 目录）。

### 重点 review 清单

1. **mock-first 定位（支线）**：`frontend/src/mock/*` 是本节交付核心——登录/三大写作/素材模板稿件全部 mock 驱动，不依赖真实后端。换真实后端只需改 `mockApi.js` 为 axios 调用（`api/http.js` 契约已对齐）。行级定位：`src/mock/mockApi.js` 顶部注释 + 各函数。
2. **SSE 增量渲染同构性**：`src/components/SseStream.vue` 的 `content.value += chunk` 逐字对应课件主角二 `content.value += e.data`，且 `defineExpose({ startStream, closeStream })` 暴露供父组件调用（`<script setup>` 中不能用 `export function`，已按 Vue 规范调整）。关键回归 `sseStream_appendsIncrementally_notFullReplace` 锁死。
3. **路由守卫 / Axios 401 可测性重构**：守卫抽为 `authGuard`（`router/index.js`）、401 处理抽为 `handleUnauthorized`（`api/http.js`）——纯展示性重构，不改变课件逻辑语义，但让关键回归可单测。review 时确认语义未漂移。
4. **数据隔离语义**：mock 按 userId 键隔离（`db.js`），测试 `manage.test.js` 的"数据隔离：用户 A 看不到用户 B 的素材/模板/稿件"锁死，对齐 H4 不变量① 的前端侧隔离。
5. **测试环境的良性告警**：`workbench.test.js` 挂载 Workbench 时 `[Vue warn] injection Symbol(router) not found`——因测试未注入 router，不影响断言（该文件验证的是 store 状态 + mock 生成链路）。若需消除可给 mount 传 router，但不影响门禁。
6. **构建体积**：主包 1.2MB（Element Plus 全量引入），mock 支线的合理简化；真实交付时可按需引入或 code-split（vite 已给出 warning）。非错误。

### 如何验证（可直接复制执行）

```bash
# 1. 全量门禁（前端课硬门禁）
cd frontend && npm run build && npm run test
# 预期：vite build SUCCESS（dist/ 产出）+ Test Files 6 passed / Tests 19 passed

# 2. 只跑关键回归
cd frontend && npx vitest run src/__tests__/routerGuard.test.js src/__tests__/httpInterceptor.test.js src/__tests__/sseStream.test.js
# 预期：3 个文件全绿（三条关键回归）

# 3. 数据隔离回归
cd frontend && npx vitest run src/__tests__/manage.test.js
# 预期：4 条全绿，含"数据隔离：用户 A 看不到用户 B"

# 4. 人工演示
cd frontend && npm run dev   # 未登录被拦 → demo/123456 登录 → 三大 Tab + 管理全流程
```

### 剩余人工项

真模型/真中间件联调（真实 writing-api + 真实 SSE + Redis 断线续传 + 素材真实入库）、真实后端替换（改 `mockApi.js`）、浏览器人工演示（`npm run dev`）。

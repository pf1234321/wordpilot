# Implementation Plan: 前端工作台（mock 支线）——先 mock 跑通，再换真实后端

**Branch**: `lesson7-frontend` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-frontend-mock-workbench/spec.md`

## Summary

按技术方案 §10 与第 7 节课件实现前端工作台，本支线按用户点名「**支线·先mock**」执行：**先以本地 mock 数据源驱动把前端完整跑起来**（登录、三大写作 Tab、素材/模板/稿件管理、SSE 增量渲染全部用 mock 模拟真实后端行为），不依赖真实后端启动；真实 `writing-api` 端点联调（`POST /api/auth/login`、`/api/writing/dialog|rag|template`、素材/模板/稿件 REST）留待后续把 mock 层替换为 axios 真实调用。前端工程落位仓库根目录 `frontend/`（独立 Vue3 工程，与 writing-* Maven 模块平级，不进 Maven 构建）。

技术栈：**Vue3 + Vite + Element Plus + Pinia + Axios + Vue Router + Vitest（Vue Test Utils）**。SSE mock 用本地定时分片模拟（每片几个字、共十几段，验证增量渲染）。

## Technical Context

**Language/Runtime**: Vue 3（`<script setup>`）+ Vite 5；Node 前端工程，独立 `package.json`，运行 `npm run build` / `npm run test`。

**Primary Dependencies**（`frontend/package.json`）:
- `vue@^3`、`vue-router@^4`、`pinia@^2`、`axios@^1`、`element-plus@^2`、`@element-plus/icons-vue`（Element Plus 图标，Tab/管理页图标）
- 开发依赖：`vite@^5`、`@vitejs/plugin-vue`、`vitest`、`@vue/test-utils`、`jsdom`、`@pinia/testing`
- **无新增后端依赖**（mock 支线不连真实后端；axios 封装保留，供后续替换真实调用）

**Mock 数据层（支线核心，对齐课件"验收 harness"与 §10 关键设计点）**:
- `frontend/src/mock/`：`db.js`（按 userId 键隔离的 mock 数据源：用户、素材、模板、稿件）；`sse.js`（`mockSseStream(callback)` 定时分片推送，每片几个字共十几段，模拟真实 SSE 增量）；`mockApi.js`（模拟后端各端点：登录、素材 CRUD、模板 CRUD、稿件 CRUD、三大写作生成）
- 登录 mock：任意非空账号密码 → 返回 Token（`mock-token-{user}`）并按用户名隔离数据；模拟"仅当前用户"
- 三大写作 mock：输入 → `mockSseStream` 分片吐字 → 拼出完整文稿 → 写入稿件历史
- 素材/模板/稿件 mock CRUD：本地数组增删改查，仅当前登录用户

**SSE 增量渲染（FR5 / 课件主角二）**:
- `frontend/src/components/SseStream.vue`：暴露 `startStream(url, params)`；`content.value += e.data` **增量追加，不整段替换**（关键回归点）；`onerror` → `es.close()` 可重连；mock 支线用 `mockSseStream` 替代真实 `EventSource`
- 关键回归测试 `sseStream_appendsIncrementally_notFullReplace`

**Axios 拦截器与路由守卫（FR2 / FR3 / 课件主角一、配角）**:
- `frontend/src/api/http.js`：请求拦截器统一 `config.headers.Authorization = token`；响应拦截器捕获 401 → 清 Token → `router.push('/login')`；登录接口放行（不携带 Token 走鉴权）
- `frontend/src/router/index.js`：`beforeEach` 守卫，无 Token 访问业务页 → 重定向 `/login`
- 关键回归测试 `routerGuard_blocksUnauthenticated_redirectsToLogin`、`tokenExpired_anyRequest401_redirectsToLogin`

**Pinia 状态（FR4 / 课件坑：三个 Tab 状态混乱）**:
- `frontend/src/stores/user.js`：token、user、login/logout
- `frontend/src/stores/workbench.js`：activeTab、各 Tab 输入/生成状态、SSE 状态

**页面模块（FR1/FR4/FR6/FR7/FR8/FR9，课件模块分工表）**:
- `frontend/src/views/Login.vue`：登录页
- `frontend/src/views/Workbench.vue`：三大 Tab 工作台（`el-tabs`：dialog / rag / template）
- `frontend/src/views/`：素材管理（MaterialManage）、模板管理（TemplateManage）、稿件历史（ArticleHistory）——或作为 Workbench 内页签；以 Workbench 内 Tab 落地
- 模板动态表单：按模板 `variables` 动态生成 `el-form` 填参（FR6）

**Testing**（harness 先行，方法名英文，课件原文进测试描述）:
- `frontend/src/__tests__/routerGuard.test.js`：**关键回归 `routerGuard_blocksUnauthenticated_redirectsToLogin`**（无 Token 访问 `/workbench` → 重定向 `/login`）
- `frontend/src/__tests__/httpInterceptor.test.js`：**关键回归 `tokenExpired_anyRequest401_redirectsToLogin`**（mock 请求返回 401 → 清 Token → 跳 `/login`）；请求拦截器带 Token 断言
- `frontend/src/__tests__/sseStream.test.js`：**关键回归 `sseStream_appendsIncrementally_notFullReplace`**（分片推送 → 内容逐步累积，断言为追加非替换）
- `frontend/src/__tests__/login.test.js`：登录 mock 拿到 Token → 写 localStorage → 跳转工作台
- `frontend/src/__tests__/workbench.test.js`：三大 Tab 各走通一次（mock SSE 渲染）
- `frontend/src/__tests__/manage.test.js`：素材/模板/稿件管理 CRUD + 仅当前用户数据隔离
- **硬门禁：`vite build` 通过 + `npm run test`（vitest run）全绿**（前端课替代 `mvn clean verify`）

**Constraints**:
- 前端独立工程，不进 Maven 构建；不依赖真实后端启动（mock 支线）
- mock 数据按当前登录用户键隔离（模拟"仅当前用户"，对齐 H4 不变量① 的隔离语义，虽为前端 mock）
- Token 存 localStorage，业务请求统一经 Axios 拦截器携带；登录接口放行
- SSE 增量追加（`+=`），不整段替换；断线关闭可重连
- 三 Tab 状态由 Pinia 统一管理，不散落各组件
- 不引入真实后端联调/真模型/真中间件（列为人工项，后续替换 mock 层）
- 测试方法名英文（如 `sseStream_appendsIncrementally_notFullReplace`），课件原文（"SSE 增量追加"等）保留在测试描述

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I 单体 7 模块单向依赖 | ✅ 前端独立工程（`frontend/`）不进 Maven，不新增后端模块，不破坏 7 模块单向依赖 |
| II Agent 无状态 | 前端不涉及 Agent（仅消费 SSE 渲染），不涉及 |
| III AgentScope 边界 | 前端不涉及 |
| IV 极简登录 | ✅ 前端登录 mock 对齐后端 `POST /api/auth/login` 契约（Token 换取、存本地、拦截器携带、401 跳登录），不引入 SpringSecurity |
| V 查询强制 user_id | ✅ mock 层按当前登录用户键隔离素材/模板/稿件（模拟"仅当前用户"隔离语义） |
| VI JPA 规范 | 前端不涉及 |
| VII RAG 管线自实现 | 前端不涉及（素材仿写 Tab 调 mock 生成，真实 RAG 在第 4/5/8 节） |
| VIII 记忆三层 | 前端不涉及 |
| IX 三类写作共享底座 | ✅ 三大 Tab（对话/仿写/模板）统一经工作台入口 + 共享 SSE 渲染（对齐 §10 三大写作入口） |
| X 可演示成果 | ✅ `vite build` + 冒烟全绿；`npm run dev` 可完整演示登录→三大 Tab→管理全流程（mock 驱动） |

## Project Structure

### Documentation (this feature)

```text
specs/007-frontend-mock-workbench/
├── spec.md              # 本节规格（已产出）
├── plan.md              # 本文件
└── tasks.md             # /speckit-tasks 输出（本 plan 不创建）
```

### Source Code (repository root)

前端工程落位 `frontend/`（独立 Vue3 工程）：

```text
frontend/
├── package.json                 # Vue3 + Vite + Element Plus + Pinia + Axios + Vitest 依赖与脚本
├── vite.config.js               # Vite 配置（插件 vue、测试环境 jsdom）
├── index.html                   # SPA 入口
├── src/
│   ├── main.js                  # 应用启动（Pinia + Router + Element Plus）
│   ├── App.vue                  # 根组件（router-view）
│   ├── router/index.js          # 路由 + 守卫（FR3）
│   ├── api/http.js              # Axios 封装：统一 Token + 401 跳登录（FR2）
│   ├── stores/user.js           # Pinia 用户状态（FR4）
│   ├── stores/workbench.js      # Pinia 工作台状态（FR4）
│   ├── mock/db.js               # mock 数据源（按 userId 隔离）
│   ├── mock/sse.js              # mockSseStream 定时分片（FR5）
│   ├── mock/mockApi.js          # mock 后端各端点（登录/素材/模板/稿件/写作）
│   ├── views/Login.vue          # 登录页（FR1）
│   ├── views/Workbench.vue      # 三大 Tab 工作台（FR4）
│   ├── views/MaterialManage.vue # 素材管理（FR7）
│   ├── views/TemplateManage.vue # 模板管理（FR8）
│   ├── views/ArticleHistory.vue # 稿件历史（FR9）
│   └── components/SseStream.vue # SSE 流式增量渲染（FR5）
│   └── __tests__/               # Vitest 冒烟（harness 先行）
│       ├── routerGuard.test.js
│       ├── httpInterceptor.test.js
│       ├── sseStream.test.js
│       ├── login.test.js
│       ├── workbench.test.js
│       └── manage.test.js
└── README.md                    # 前端运行说明（npm install / dev / build / test）
```

## Quickstart（验证指南）

前置：Node（≥18）+ npm。首次 `cd frontend && npm install`。

- **构建门禁**：`cd frontend && npm run build` → 期望 `vite build` 成功、产物输出 `dist/`。
- **冒烟**：`cd frontend && npm run test` → 期望 vitest 全绿（登录/路由守卫/401/SSE 增量/三大 Tab/管理全流程）。
- **人工演示**：`cd frontend && npm run dev` → 打开本地地址：未登录访问工作台被拦；登录后三大 Tab 各写一篇（SSE 逐段吐字）；素材/模板/稿件管理全流程仅当前用户。

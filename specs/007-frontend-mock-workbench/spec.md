# Feature Specification: 前端工作台（mock 支线）——先 mock 跑通，再换真实后端

**Feature Branch**: `lesson7-frontend`
**Created**: 2026-10-04
**Status**: Draft
**Input**: User description: 第7节需求：前端工作台（Vue3 + Vite + Element Plus + Pinia + Axios + SSE）——一个单页面 Tab 工作台，把登录、三大写作（一句话多轮 / 素材仿写 / 模板写作）、素材管理、模板管理、稿件历史收进一个页面，用 SSE 流式实时渲染大模型输出。技术方案 §10。本支线按用户点名「**支线·先mock**」执行：**先以 mock 数据驱动把前端工作台完整跑起来**（登录、三大 Tab、素材/模板/稿件管理、SSE 增量渲染全部用本地 mock 模拟真实后端行为），后端联调留待后续替换真实 `writing-api` 端点。前端工程落位仓库根目录 `frontend/`。

## 用户场景与测试

### User Story 1 — 登录与路由守卫：未登录拦截、登录拿 Token、401 跳登录（P1）

用户在登录页输入账号密码；合法则获得 Token 并存入本地存储，进入工作台；未登录访问任何业务页被路由守卫重定向到登录页；已登录状态下任意请求返回 401（Token 失效）则全局跳登录。所有业务请求由 Axios 拦截器统一携带 Token，业务代码不逐请求手写。

**Why this priority**：登录是工作台的入口，路由守卫与 Axios 拦截器是课件点名的两大坑（未登录访问空页 / Token 失效不清 / 登录接口也被带 Token）。先 mock 跑通登录闭环，后续换真实 `POST /api/auth/login` 只改 mock 层。

**Independent Test**：路由守卫在无 Token 时把 `→ /workbench` 重定向到 `/login`；mock 登录返回 Token 后成功进入工作台。

**Acceptance Scenarios**:

1. **Given** 本地无 Token，**When** 访问 `/workbench`，**Then** 路由守卫重定向到 `/login`。
2. **Given** 在登录页输入任意非空账号密码（mock 模式），**When** 点击登录，**Then** 获得 Token、写入 localStorage、跳转 `/workbench`。
3. **Given** 已登录且本地存有 Token，**When** 发起任意业务请求，**Then** Axios 请求拦截器统一把 Token 写入 `Authorization` 头。
4. **Given** 某请求返回 401，**When** 响应拦截器捕获，**Then** 清 Token 并全局跳转 `/login`。

---

### User Story 2 — 三大 Tab 工作台 + SSE 流式增量渲染（P1）

工作台提供三个 Tab：一句话多轮写作（对话式输入、多轮、SSE 流式输出）、素材仿写（选素材 + 输入需求 → 仿写）、模板写作（选模板 → 按 variables 动态生成表单 → 生成）。三大 Tab 的生成结果都走 SSE 流式：**逐段增量追加渲染，不整段替换**；断线时关闭连接可重连。Tab 状态由 Pinia 统一管理，不散落各组件自存。

**Why this priority**：三大写作能力是工作台核心价值；SSE 增量渲染是长文生成体验的关键（课件点名"大段内容一次性刷 → 卡顿"，必须增量追加）。mock 支线用定时分片模拟 SSE 逐段推送，验证增量渲染逻辑。

**Independent Test**：`SseStream` 组件在收到分片数据时对内容做增量追加而非整段替换（可用 mock 定时分片断言内容逐步累积）。

**Acceptance Scenarios**:

1. **Given** 在"一句话写作"Tab 输入一句话，**When** 触发生成，**Then** SSE 逐段返回增量内容并实时上屏，最终拼接为完整文稿。
2. **Given** SSE 连接中断，**When** 触发 onerror，**Then** 连接被关闭（可重连，不无限报错）。
3. **Given** 在"素材仿写"Tab 选择素材并输入需求，**When** 触发生成，**Then** 走通仿写并 SSE 渲染。
4. **Given** 在"模板写作"Tab 选择模板，**When** 按模板 variables 动态生成的表单填写并提交，**Then** 走通模板写作并 SSE 渲染。

---

### User Story 3 — 素材 / 模板 / 稿件管理（仅当前用户）（P2）

工作台附带三类管理：素材管理（上传、列表、预览、删除）、模板管理（新增、编辑、启用停用、变量配置）、稿件历史（记录、重新编辑、导出、删除）。mock 支线用本地 mock 数据源模拟"仅当前用户"的数据隔离（不同登录用户看到各自数据）。

**Why this priority**：素材/模板/稿件是系统内沉淀复用的资产，管理是工作台完整闭环的组成；mock 先建出可操作的管理界面与交互。

**Independent Test**：素材列表只展示当前登录用户的素材；新增/删除后列表随之更新。

**Acceptance Scenarios**:

1. **Given** 某用户登录并已有 mock 素材，**When** 打开素材管理，**Then** 只看到该用户的素材，可上传、预览、删除。
2. **Given** 模板管理页，**When** 新增/编辑模板、启用停用、配置 variables，**Then** 操作生效并持久到 mock 数据源。
3. **Given** 稿件历史页，**When** 查看记录、重新编辑、导出、删除，**Then** 各操作闭环可用。

---

### Edge Cases

- **未登录访问业务页**：路由守卫重定向到登录页，不出现空页面。
- **Token 失效（401）**：响应拦截器捕获 401 → 清 Token → 跳登录，所有后续请求不再携带失效 Token。
- **登录接口被带 Token**：登录请求必须放行（不携带旧 Token / 不走鉴权拦截），否则永远 401。
- **SSE 大段内容一次性刷**：必须增量追加（`content += chunk`），不得整段替换。
- **三个 Tab 状态混乱**：Pinia 统一管理，切 Tab 不丢当前输入与生成状态。
- **mock 数据隔离**：用户 A 看不到用户 B 的素材/模板/稿件。

---

## 功能需求（FR，从课件"想清楚"提炼，每条可测试）

- **FR1 登录模块**：账号密码登录获得 Token；Token 存本地存储；路由守卫拦截未登录；登录接口放行（不被鉴权拦截）。
- **FR2 Axios 封装（api/*.js）**：请求拦截器统一携带 Token；响应拦截器统一处理 401 跳登录；业务请求不逐请求手写 Token。
- **FR3 路由守卫（router/index.js）**：未登录访问业务页重定向登录页。
- **FR4 三大 Tab 工作台（views/Workbench.vue）**：一句话多轮写作、素材仿写、模板写作三个 Tab，由 Pinia 统一管理状态。
- **FR5 SSE 流式渲染（components/SseStream.vue）**：增量追加渲染（`content += e.data`），断线关闭可重连；mock 支线用定时分片模拟 SSE 逐段推送。
- **FR6 模板动态表单**：按模板 variables 动态生成表单填参。
- **FR7 素材管理**：上传、列表、预览、删除（mock 数据源，仅当前用户）。
- **FR8 模板管理**：新增、编辑、启用停用、变量配置（mock 数据源）。
- **FR9 稿件历史**：记录、重新编辑、导出、删除（mock 数据源）。

## Key Entities

- **User（当前用户）**：由登录产生的会话主体；Token 标识；mock 数据按 user 隔离。
- **Material（素材）**：用户上传的文档资源（mock 元数据），用于仿写 Tab 选择与素材管理。
- **Template（模板）**：含 variables 配置的写作模板，用于模板 Tab 与模板管理。
- **Article（稿件）**：三大写作产出的文稿记录，含内容与来源模式，用于稿件历史。

## 成功标准（Success Criteria）

### Measurable Outcomes

- **SC-001**：全新浏览器未登录直接访问工作台，100% 被重定向到登录页。
- **SC-002**：登录后三大 Tab 各走通一次生成，SSE 实时增量吐字、无整段卡顿替换。
- **SC-003**：素材/模板/稿件管理全流程可操作，且仅呈现当前用户数据（mock 隔离生效）。
- **SC-004**：`vite build` 通过 + 前端冒烟全绿（硬门禁，替代 mvn verify）。

## Assumptions

- **mock-first 为支线定位**：本节前端以 mock 数据驱动完整跑通，**不依赖真实后端启动**；真实 `writing-api` 端点联调、SSE 真实流、Redis 断线续传属后续替换（换 mock 层为 axios 真实调用即可），本节不做真模型/真中间件联调（列为人工项）。
- **测试框架**：前端冒烟用 Vitest（Vue3 生态标准），关键回归测试用英文方法名落地（按技能写后门禁），课件原文进 `@DisplayName` 等价物（`test()` 描述或 it 标题）。
- **目录**：前端工程落位仓库根目录 `frontend/`（与 writing-* Maven 模块平级），独立 `package.json`，不进 Maven 构建。
- **依赖**：Vue3 + Vite + Element Plus + Pinia + Axios + Vue Router + Vitest。SSE mock 用本地定时分片模拟。
- **数据隔离**：mock 层按当前登录用户键隔离素材/模板/稿件，模拟"仅当前用户"。

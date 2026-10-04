# 第 7 节 前端工作台（mock 支线）— 任务清单（/speckit-tasks）

**Branch**: `lesson7-frontend` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

实现完成定义（前端课，替代 mvn verify）：**`cd frontend && npm run build`（vite build）通过 + `npm run test`（vitest run）全绿**。

## 前置（已就绪）

- 分支 `lesson7-frontend` 已建（基于 `lesson5-agent-core` 工作区，未提交的第 5 节改动随分支保留，本分支不自动 commit）
- 课件 / TechnicalSolution §10 已读；前序 1~6 节交付物存在性已核对（`LoginController`/`UserContext`/`ModelService`/`MaterialService`/`AgentFactory`/`TemplateService`/`TemplateRepository` 等均存在，见 spec.md 依赖节）
- 前端工程落位 `frontend/`（独立 Vue3 工程，不进 Maven 构建）
- **支线定位**：mock 数据驱动完整跑通，不依赖真实后端启动；真实 writing-api 联调列后续替换

## 任务清单

| # | 模块 | 任务 | 验证 |
|---|---|---|---|
| T001 | frontend | `frontend/package.json` + `vite.config.js` + `index.html` + `.gitignore`：初始化 Vue3+Vite 工程，依赖 vue/vue-router/pinia/axios/element-plus/@element-plus/icons-vue，devDeps vite/@vitejs/plugin-vue/vitest/@vue/test-utils/jsdom/@pinia/testing；脚本 `dev`/`build`/`test`（vitest run）；vite.config 配 plugin-vue + test.jsdom | `npm install` 成功、`npm run build` 出 `dist/` |
| T002 | frontend | `src/main.js` + `src/App.vue`：应用启动（createApp + Pinia + Router + Element Plus 全量注册）；App 挂 `router-view` | 编译通过 |
| T003 | frontend | `src/router/index.js`（FR3）：路由表 `/login` + `/workbench` + 业务子路由；`beforeEach` 守卫——无 Token 访问业务页重定向 `/login`（课件配角逐字保真：`if (to.path !== '/login' && !token) return '/login'`） | 编译通过 |
| T004 | frontend | `src/api/http.js`（FR2）：Axios 实例 + 请求拦截器统一 `config.headers.Authorization = token`（课件主角一）；响应拦截器捕获 401 → 清 Token → `router.push('/login')`；登录接口放行 | 编译通过 |
| T005 | frontend | `src/mock/db.js`（支线核心）：按 userId 键隔离的 mock 数据源——预置用户、素材、模板（含 variables）、稿件；提供增删改查纯函数（仅当前用户） | 编译通过 |
| T006 | frontend | `src/mock/sse.js`：`mockSseStream(callback, content)` 定时分片推送（每片几个字、共十几段，模拟真实 SSE 增量）；支持中断关闭 | 编译通过 |
| T007 | frontend | `src/mock/mockApi.js`：模拟后端端点——`login`（任意非空账号密码 → Token `mock-token-{user}`）、素材 CRUD、模板 CRUD、稿件 CRUD、三大写作生成（`mockSseStream` 分片 → 拼完整文稿 → 写稿件历史） | 编译通过 |
| T008 | frontend | `src/stores/user.js`（FR4）：Pinia——token、user、`login()`（调 mockApi）、`logout()`（清 token 跳登录）、`isAuthenticated` | 编译通过 |
| T009 | frontend | `src/stores/workbench.js`（FR4）：Pinia——activeTab（dialog/rag/template）、各 Tab 输入/生成状态、SSE 状态（避免三 Tab 状态混乱，课件坑） | 编译通过 |
| T010 | frontend | `src/components/SseStream.vue`（FR5，课件主角二）：暴露 `startStream(url, params)`；`content.value += e.data` **增量追加不整段替换**；`onerror → es.close()` 断线关闭可重连；mock 支线内部调 `mockSseStream` | 编译通过 |
| T011 | frontend | `src/views/Login.vue`（FR1）：登录页（账号密码 + el-form 校验）→ `user.login()` → 跳 `/workbench` | 编译通过 |
| T012 | frontend | `src/views/Workbench.vue`（FR4，课件主角三）：`el-tabs` 三 Tab——一句话写作（dialog）、素材仿写（rag，选素材 + 需求）、模板写作（template，选模板 → 按 variables 动态生成 el-form 填参）；各 Tab 调 mockApi 生成 → SseStream 渲染；三 Tab 状态经 workbench store | 编译通过 |
| T013 | frontend | `src/views/MaterialManage.vue`（FR7）：素材管理——上传、列表、预览、删除（mock 数据源，仅当前用户） | 编译通过 |
| T014 | frontend | `src/views/TemplateManage.vue`（FR8）：模板管理——新增、编辑、启用停用、变量配置（mock 数据源） | 编译通过 |
| T015 | frontend | `src/views/ArticleHistory.vue`（FR9）：稿件历史——记录、重新编辑（回填对应 Tab）、导出、删除（mock 数据源） | 编译通过 |
| T016 | frontend | `src/__tests__/routerGuard.test.js`（harness）：**关键回归 `routerGuard_blocksUnauthenticated_redirectsToLogin`**（无 Token 访问 `/workbench` → 守卫重定向 `/login`）；已登录放行 | vitest 绿 |
| T017 | frontend | `src/__tests__/httpInterceptor.test.js`（harness）：**关键回归 `tokenExpired_anyRequest401_redirectsToLogin`**（mock 请求返回 401 → 清 Token → 跳 `/login`）；**请求拦截器带 Token 断言** | vitest 绿 |
| T018 | frontend | `src/__tests__/sseStream.test.js`（harness）：**关键回归 `sseStream_appendsIncrementally_notFullReplace`**（分片推送 → 内容逐步累积，断言为追加非整段替换） | vitest 绿 |
| T019 | frontend | `src/__tests__/login.test.js`（harness）：登录 mock 拿 Token → 写 localStorage → 跳 `/workbench`；未登录访问业务页被拦 | vitest 绿 |
| T020 | frontend | `src/__tests__/workbench.test.js`（harness）：三大 Tab 各走通一次（对话/仿写/模板，mock SSE 渲染到完整文稿） | vitest 绿 |
| T021 | frontend | `src/__tests__/manage.test.js`（harness）：素材/模板/稿件管理 CRUD + **仅当前用户数据隔离**（A 看不到 B 数据） | vitest 绿 |
| T022 | frontend | `frontend/README.md`：运行说明（npm install / dev / build / test + mock 支线说明与后续替换真实后端方式） | 文档完成 |
| T023 | 全部 | 硬门禁：`cd frontend && npm run build`（vite build 通过）+ `npm run test`（vitest 全绿） | build + test 全绿 |
| T024 | specs | 验收报告 `specs/007-frontend-mock-workbench/acceptance.md`（技能第 7 步六项证据 DoD + 剩余人工项） | 报告完成 |

## 软停点：任务清单 ↔ 课件"本节交付物"自动比对

| 课件交付物（§三代码 + §四 harness） | 落地 | 说明 |
|---|---|---|
| `router/index.js`（路由 + 守卫） | ✅ T003 + T016 | 未登录拦截重定向 `/login` |
| `api/*.js`（Axios 封装：统一 Token + 401） | ✅ T004 + T017 | 请求拦截器带 Token；401 清 Token 跳登录 |
| `stores/user.js` / `stores/workbench.js`（Pinia） | ✅ T008 + T009 | 用户状态 + 工作台三 Tab 状态统一管理 |
| `views/Login.vue` | ✅ T011 + T019 | 账号密码登录拿 Token，路由守卫拦截未登录 |
| `views/Workbench.vue`（三大 Tab） | ✅ T012 + T020 | 对话/仿写/模板各走通一次，SSE 渲染 |
| `components/SseStream.vue`（SSE 流式渲染） | ✅ T010 + T018 | 增量追加不整段替换；断线关闭可重连 |
| 素材管理（上传/列表/预览/删除） | ✅ T013 + T021 | mock 数据源，仅当前用户 |
| 模板管理（新增/编辑/启停/变量配置） | ✅ T014 + T021 | mock 数据源；动态表单在 T012 |
| 稿件历史（记录/重编/导出/删除） | ✅ T015 + T021 | mock 数据源 |
| 关键回归：`routerGuard_blocksUnauthenticated_redirectsToLogin` | ✅ T016 | 无 Token 访问业务页 → 重定向 `/login` |
| 关键回归：`tokenExpired_anyRequest401_redirectsToLogin` | ✅ T017 | 401 → 清 Token → 跳登录 |
| 关键回归：`sseStream_appendsIncrementally_notFullReplace` | ✅ T018 | `content += chunk` 增量追加 |
| （支线补充）mock 数据层 `db/sse/mockApi` | ✅ T005 + T006 + T007 | 用户点名「先 mock」：本地数据源 + 定时分片模拟 SSE，后续替换为 axios 真实调用 |
| （支线补充）模板动态表单（variables → el-form） | ✅ T012 | 课件模块分工表点名"模板 Tab 按 variables 动态生成表单" |
| （支线补充）三 Tab 状态 Pinia 统一 | ✅ T009 | 课件坑"三个 Tab 状态混乱 → Pinia 统一管理" |

**比对结论**：任务清单与课件交付物**一致**（登录/路由/三大 Tab/SSE/素材/模板/稿件 + 三条关键回归 harness 全覆盖；mock 数据层为用户点名「支线·先mock」的明确补充，非文档外新增对外概念）。测试任务（T016~T021）先行/伴随实现（T003/T004/T010/T011/T012/T013/T014/T015）。**关键回归断言逻辑逐条保真，方法名英文（如 `sseStream_appendsIncrementally_notFullReplace`），课件原文（"SSE 增量追加"等）保留在测试描述。** 前端为全新独立工程，不触碰任何前序 Java 文件。

**停止点：以上任务清单与比对结果请确认，确认后进入 implement。**

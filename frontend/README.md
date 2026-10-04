# ScriptAgent 智稿引擎 · 前端工作台（mock 支线）

ScriptAgent 前端工作台：单页面 Tab 工作台，把登录、三大写作（一句话多轮 / 素材仿写 / 模板写作）、素材管理、模板管理、稿件历史收进一个页面，用 SSE 流式实时渲染大模型输出。

技术栈：**Vue3 + Vite + Element Plus + Pinia + Axios + SSE**（TechnicalSolution §10）。

## 支线定位：先 mock 跑通，再换真实后端

本支线按「**支线·先 mock**」执行：**前端先用本地 mock 数据源完整跑起来**，不依赖真实后端启动。

- mock 登录：`demo / 123456` 或 `alice / 123456`
- mock 数据：素材 / 模板 / 稿件按当前用户键隔离（模拟"仅当前用户"）
- mock SSE：`src/mock/sse.js` 用定时分片逐段推送，验证增量渲染（不整段替换）
- 三大写作：`src/mock/mockApi.js` 模拟 `POST /api/writing/dialog|rag|template`，生成结果写入稿件历史

### 换真实后端（后续）

`src/mock/mockApi.js` 中各函数改为调用 `src/api/http.js` 的真实 axios 请求即可（契约已对齐：
登录 `POST /api/auth/login`、写作 `POST /api/writing/dialog|rag|template`、素材/模板/稿件 REST）。
Axios 拦截器已统一携带 Token + 401 跳登录，无需改动业务页面。

## 运行

前置：Node ≥ 18 + npm。

```bash
cd frontend
npm install
```

### 开发

```bash
npm run dev
```

打开本地地址（默认 http://localhost:5173）：未登录访问工作台被路由守卫拦截到登录页；登录后三大 Tab
各写一篇（SSE 逐段吐字）；素材/模板/稿件管理全流程仅当前用户。

### 构建（硬门禁）

```bash
npm run build   # vite build 通过 → 产物 dist/
```

### 测试（硬门禁）

```bash
npm run test    # vitest run 全绿
```

## 目录

```text
src/
├── main.js                 # 应用启动（Pinia + Router + Element Plus）
├── App.vue                 # 根组件
├── router/index.js         # 路由 + 守卫（未登录拦截）
├── api/http.js             # Axios 封装：统一 Token + 401 跳登录
├── stores/user.js          # Pinia 用户状态
├── stores/workbench.js     # Pinia 工作台状态（三大 Tab 统一管理）
├── mock/                   # mock 数据层（支线核心）
│   ├── db.js               # 按 userId 隔离的数据源
│   ├── sse.js              # mockSseStream 定时分片
│   └── mockApi.js          # mock 后端各端点
├── views/
│   ├── Login.vue           # 登录页
│   ├── Workbench.vue       # 三大 Tab 工作台
│   ├── MaterialManage.vue  # 素材管理
│   ├── TemplateManage.vue  # 模板管理
│   └── ArticleHistory.vue  # 稿件历史
└── components/SseStream.vue# SSE 流式增量渲染
└── __tests__/              # Vitest 冒烟（关键回归）
```

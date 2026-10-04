// mock 后端 API：模拟真实 writing-api 端点，返回 Promise（与 axios 一致的异步契约）
// 前端 mock 支线核心——登录、素材 CRUD、模板 CRUD、稿件 CRUD、三大写作生成
// 后续替换真实后端：将本文件各函数改为调用 api/http.js 的真实 axios 请求即可

import {
  findUser,
  userToken,
  listMaterials,
  addMaterial,
  getMaterial,
  deleteMaterial,
  listTemplates,
  addTemplate,
  updateTemplate,
  deleteTemplate,
  listArticles,
  addArticle,
  deleteArticle
} from './db'
import { mockSseStream } from './sse'

function currentUserId() {
  // mock 模式：token = `mock-token-{username}`，从中解析 userId
  const token = localStorage.getItem('token')
  if (!token) return 'guest'
  return token.replace('mock-token-', '')
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// ---- 登录：POST /api/auth/login（mock：任意非空账号密码 → 合法用户才放行）----
export async function mockLogin({ username, password }) {
  await delay(120)
  const user = findUser(username, password)
  if (!user) {
    const err = new Error('用户名或密码错误')
    err.status = 401
    throw err
  }
  return { token: userToken(username), nickname: user.nickname }
}

// ---- 素材：POST /api/material/upload + GET /api/material/list + DELETE /api/material/{id} ----
export async function mockUploadMaterial({ name, type, size, content }) {
  await delay(100)
  const userId = currentUserId()
  return addMaterial(userId, { name, type, size, content })
}

export async function mockListMaterials() {
  await delay(60)
  return listMaterials(currentUserId())
}

export async function mockPreviewMaterial(id) {
  await delay(40)
  return getMaterial(currentUserId(), id)
}

export async function mockDeleteMaterial(id) {
  await delay(60)
  return deleteMaterial(currentUserId(), id)
}

// ---- 模板：POST/GET/PATCH/DELETE /api/template ----
export async function mockCreateTemplate({ name, variables }) {
  await delay(100)
  return addTemplate(currentUserId(), { name, variables, enabled: true })
}

export async function mockListTemplates() {
  await delay(60)
  return listTemplates(currentUserId())
}

export async function mockUpdateTemplate(id, patch) {
  await delay(60)
  return updateTemplate(currentUserId(), id, patch)
}

export async function mockDeleteTemplate(id) {
  await delay(60)
  return deleteTemplate(currentUserId(), id)
}

// ---- 稿件：GET/DELETE /api/article ----
export async function mockListArticles() {
  await delay(60)
  return listArticles(currentUserId())
}

export async function mockDeleteArticle(id) {
  await delay(60)
  return deleteArticle(currentUserId(), id)
}

// ---- 三大写作生成：POST /api/writing/dialog|rag|template ----
// 返回 { stream }，调用方用 stream(onChunk, onDone) 消费增量；生成完成写入稿件历史
export async function mockGenerate({ mode, prompt, materialName, templateName, variables }) {
  await delay(80)
  const userId = currentUserId()

  const modeLabel = { dialog: '一句话写作', rag: '素材仿写', template: '模板写作' }[mode] || mode
  const heading = `【${modeLabel}】${prompt || '（无输入）'}`

  const lines = [
    `${heading}`,
    `这是一段由 mock 数据驱动生成的模拟文稿。`,
    `当前模式：${modeLabel}${materialName ? `，参考素材：${materialName}` : ''}${templateName ? `，使用模板：${templateName}` : ''}。`,
    `本支线采用「先 mock 跑通」策略：`,
    `1. 前端三大工作台已用本地 mock 数据源完整驱动；`,
    `2. SSE 用定时分片逐段推送，验证增量渲染不整段替换；`,
    `3. 素材/模板/稿件按当前用户键隔离，仅展示本人数据；`,
    `4. 后续把 mock 层替换为真实 writing-api 调用即可联调后端。`,
    `（variables: ${variables && variables.length ? variables.join('、') : '无'}）`
  ]
  const content = lines.join('\n')

  return {
    content,
    stream(onChunk, onDone) {
      return mockSseStream(content, {
        chunkSize: 6,
        intervalMs: 20,
        onChunk,
        onDone: () => {
          addArticle(userId, { mode, title: prompt || modeLabel, content })
          if (typeof onDone === 'function') onDone()
        }
      })
    }
  }
}

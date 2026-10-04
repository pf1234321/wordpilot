// 真实后端 API 层：把前端视图/商店从 mock 切换到 writing-api 真实端点（登录/素材/模板/稿件/三大写作 SSE）
// mock 层保留在 src/mock（第 7 节交付物 + 测试依赖），本层为运行时全链路入口。

import http, { handleUnauthorized } from './http'

// ---- 登录：POST /api/auth/login → ApiResponse{ data: token } ----
export async function login({ username, password }) {
  const { data } = await http.post('/auth/login', { username, password })
  return { token: data.data, nickname: username }
}

// ---- 素材：/api/material ----
export async function listMaterials() {
  const { data } = await http.get('/material')
  return data.data?.content || []
}

export async function uploadMaterial(file) {
  const fd = new FormData()
  fd.append('file', file)
  const { data } = await http.post('/material', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return data.data
}

export async function previewMaterial(id) {
  const { data } = await http.get(`/material/${id}`)
  return data.data
}

export async function deleteMaterial(id) {
  await http.delete(`/material/${id}`)
}

// ---- 模板：/api/template ----
export async function listTemplates() {
  const { data } = await http.get('/template')
  return data.data || []
}

export async function createTemplate(payload) {
  const { data } = await http.post('/template', payload)
  return data.data
}

export async function updateTemplate(id, patch) {
  const { data } = await http.patch(`/template/${id}`, patch)
  return data.data
}

export async function deleteTemplate(id) {
  await http.delete(`/template/${id}`)
}

// ---- 稿件：/api/article ----
export async function listArticles() {
  const { data } = await http.get('/article')
  return data.data?.content || []
}

export async function previewArticle(id) {
  const { data } = await http.get(`/article/${id}`)
  return data.data
}

export async function deleteArticle(id) {
  await http.delete(`/article/${id}`)
}

export async function exportArticle(id) {
  const resp = await http.post(`/article/${id}/export`, null, { responseType: 'blob' })
  return resp.data
}

// ---- 三大写作：POST /api/writing/{dialog|rag|template}，返回 SseEmitter（SSE 流） ----
// EventSource 不支持 POST body，用 fetch + ReadableStream 解析 SSE data 帧增量回调。
export function generate(mode, payload) {
  const controller = new AbortController()
  const headers = { 'Content-Type': 'application/json' }
  const token = localStorage.getItem('token')
  if (token) headers.Authorization = token

  const stream = (onChunk, onDone, onError) => {
    fetch(`/api/writing/${mode}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal: controller.signal
    })
      .then((resp) => {
        if (!resp.ok || !resp.body) {
          if (resp.status === 401) handleUnauthorized()
          const err = new Error(`写作请求失败（${resp.status}）`)
          throw err
        }
        const reader = resp.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        const pump = () => {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                if (typeof onDone === 'function') onDone()
                return
              }
              buffer += decoder.decode(value, { stream: true })
              let idx
              while ((idx = buffer.indexOf('\n')) >= 0) {
                const line = buffer.slice(0, idx).trim()
                buffer = buffer.slice(idx + 1)
                if (!line) continue
                if (line.startsWith('data:')) {
                  if (typeof onChunk === 'function') onChunk(line.slice(5).trim())
                } else if (line.startsWith('event:error') || line.startsWith('error:')) {
                  if (typeof onError === 'function') onError(new Error(line))
                }
              }
              pump()
            })
            .catch((e) => {
              if (typeof onError === 'function') onError(e)
            })
        }
        pump()
      })
      .catch((e) => {
        if (typeof onError === 'function') onError(e)
      })
  }

  const close = () => controller.abort()
  return { stream, close }
}

// mock 数据源：按 userId 键隔离，模拟"仅当前用户"
// 前端 mock 支线核心——本地数据源承载登录、素材、模板、稿件，后续替换为真实后端调用

// 预置用户：登录后 token = `mock-token-{username}`
const USERS = [
  { username: 'demo', password: '123456', nickname: '演示用户' },
  { username: 'alice', password: '123456', nickname: 'Alice' }
]

// 按 userId 隔离的业务数据（key 为 userId）
const db = {
  materials: {}, // { userId: [ {id, name, type, size, createdAt, content} ] }
  templates: {}, // { userId: [ {id, name, enabled, variables, createdAt} ] }
  articles: {} //   { userId: [ {id, mode, title, content, createdAt} ] }
}

let idSeq = 1000
function nextId() {
  idSeq += 1
  return idSeq
}

// ---- 用户 ----
export function findUser(username, password) {
  return USERS.find((u) => u.username === username && u.password === password) || null
}

export function userToken(username) {
  return `mock-token-${username}`
}

function ensureList(map, userId) {
  if (!map[userId]) {
    map[userId] = []
  }
  return map[userId]
}

// ---- 素材（仅当前用户）----
export function listMaterials(userId) {
  return ensureList(db.materials, userId).map((m) => ({ ...m }))
}

export function addMaterial(userId, { name, type, size, content }) {
  const material = {
    id: nextId(),
    name,
    type,
    size,
    content,
    createdAt: new Date().toISOString()
  }
  ensureList(db.materials, userId).push(material)
  return { ...material }
}

export function getMaterial(userId, id) {
  const found = ensureList(db.materials, userId).find((m) => m.id === id)
  return found ? { ...found } : null
}

export function deleteMaterial(userId, id) {
  const list = ensureList(db.materials, userId)
  const idx = list.findIndex((m) => m.id === id)
  if (idx >= 0) {
    list.splice(idx, 1)
    return true
  }
  return false
}

// ---- 模板（仅当前用户）----
export function listTemplates(userId) {
  return ensureList(db.templates, userId).map((t) => ({ ...t }))
}

export function addTemplate(userId, { name, variables, enabled }) {
  const template = {
    id: nextId(),
    name,
    variables: Array.isArray(variables) ? variables : [],
    enabled: enabled !== false,
    createdAt: new Date().toISOString()
  }
  ensureList(db.templates, userId).push(template)
  return { ...template }
}

export function updateTemplate(userId, id, patch) {
  const list = ensureList(db.templates, userId)
  const t = list.find((x) => x.id === id)
  if (!t) return null
  Object.assign(t, patch)
  return { ...t }
}

export function deleteTemplate(userId, id) {
  const list = ensureList(db.templates, userId)
  const idx = list.findIndex((t) => t.id === id)
  if (idx >= 0) {
    list.splice(idx, 1)
    return true
  }
  return false
}

// ---- 稿件历史（仅当前用户）----
export function listArticles(userId) {
  return ensureList(db.articles, userId).map((a) => ({ ...a }))
}

export function addArticle(userId, { mode, title, content }) {
  const article = {
    id: nextId(),
    mode,
    title: title || '未命名稿件',
    content,
    createdAt: new Date().toISOString()
  }
  ensureList(db.articles, userId).push(article)
  return { ...article }
}

export function deleteArticle(userId, id) {
  const list = ensureList(db.articles, userId)
  const idx = list.findIndex((a) => a.id === id)
  if (idx >= 0) {
    list.splice(idx, 1)
    return true
  }
  return false
}

export function resetDb() {
  db.materials = {}
  db.templates = {}
  db.articles = {}
  idSeq = 1000
}

export { db }

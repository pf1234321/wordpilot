import { describe, it, expect, beforeEach } from 'vitest'
import { resetDb } from '../mock/db'
import {
  mockUploadMaterial,
  mockListMaterials,
  mockDeleteMaterial,
  mockCreateTemplate,
  mockListTemplates,
  mockUpdateTemplate,
  mockDeleteTemplate,
  mockGenerate,
  mockListArticles,
  mockDeleteArticle
} from '../mock/mockApi'

// 素材/模板/稿件管理冒烟：CRUD + 仅当前用户数据隔离
describe('素材/模板/稿件管理', () => {
  beforeEach(() => {
    localStorage.clear()
    resetDb()
  })

  it('素材：上传 → 列表 → 删除', async () => {
    localStorage.setItem('token', 'mock-token-demo')
    const m = await mockUploadMaterial({ name: 'a.txt', type: 'txt', size: '1 KB', content: '素材A' })
    expect(m.id).toBeTruthy()
    let list = await mockListMaterials()
    expect(list.length).toBe(1)
    await mockDeleteMaterial(m.id)
    list = await mockListMaterials()
    expect(list.length).toBe(0)
  })

  it('模板：创建 → 列表 → 启停 → 删除', async () => {
    localStorage.setItem('token', 'mock-token-demo')
    const t = await mockCreateTemplate({ name: '模板X', variables: [{ key: 'v1' }] })
    expect(t.enabled).toBe(true)
    await mockUpdateTemplate(t.id, { enabled: false })
    const list = await mockListTemplates()
    expect(list[0].enabled).toBe(false)
    await mockDeleteTemplate(t.id)
    expect((await mockListTemplates()).length).toBe(0)
  })

  it('稿件：生成后写入历史，可删除', async () => {
    localStorage.setItem('token', 'mock-token-demo')
    const { stream } = await mockGenerate({ mode: 'dialog', prompt: '测试稿件' })
    await new Promise((resolve) => {
      stream(() => {}, () => resolve())
    })
    await new Promise((r) => setTimeout(r, 60))
    let articles = await mockListArticles()
    expect(articles.length).toBe(1)
    await mockDeleteArticle(articles[0].id)
    articles = await mockListArticles()
    expect(articles.length).toBe(0)
  })

  it('数据隔离：用户 A 看不到用户 B 的素材/模板/稿件', async () => {
    // A 上传素材
    localStorage.setItem('token', 'mock-token-alice')
    await mockUploadMaterial({ name: 'alice.txt', type: 'txt', size: '1 KB', content: 'A素材' })
    await mockCreateTemplate({ name: 'alice模板', variables: [] })
    const { stream: aliceStream } = await mockGenerate({ mode: 'dialog', prompt: 'alice稿件' })
    await new Promise((resolve) => aliceStream(() => {}, () => resolve()))
    await new Promise((r) => setTimeout(r, 80))

    // B 登录，看不到 A 的任何数据
    localStorage.setItem('token', 'mock-token-demo')
    expect((await mockListMaterials()).length).toBe(0)
    expect((await mockListTemplates()).length).toBe(0)
    expect((await mockListArticles()).length).toBe(0)

    // A 仍可见自己的数据
    localStorage.setItem('token', 'mock-token-alice')
    expect((await mockListMaterials()).length).toBe(1)
    expect((await mockListTemplates()).length).toBe(1)
    expect((await mockListArticles()).length).toBe(1)
  })
})

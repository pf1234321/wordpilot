import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 全链路：登录走真实 /api/auth/login（writingApi.login），测试 mock 该 API 层验证 store 逻辑
vi.mock('../api/writingApi', () => ({
  login: vi.fn(async ({ username, password }) => {
    if (password !== '123456') throw new Error('账号或密码错误')
    return { token: 'tok-' + username, nickname: username }
  })
}))

import { useUserStore } from '../stores/user'

// 登录冒烟：账号密码登录拿 Token；未登录访问业务页被拦（守卫在 routerGuard 测试覆盖）
describe('登录模块', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('合法账号密码登录后拿到 Token 并写入 localStorage', async () => {
    const store = useUserStore()
    const token = await store.login({ username: 'demo', password: '123456' })
    expect(token).toBe('tok-demo')
    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('token')).toBe('tok-demo')
  })

  it('错误密码登录抛错且不写 Token', async () => {
    const store = useUserStore()
    await expect(store.login({ username: 'demo', password: 'wrong' })).rejects.toThrow('账号或密码错误')
    expect(localStorage.getItem('token')).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('登出清除 Token', async () => {
    const store = useUserStore()
    await store.login({ username: 'demo', password: '123456' })
    store.logout()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })
})

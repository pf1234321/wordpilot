import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import http, { handleUnauthorized } from '../api/http'
import router from '../router'

// 关键回归：tokenExpired_anyRequest401_redirectsToLogin
// 课件原文：401 → 清 Token → 跳登录
describe('Axios 拦截器：统一 Token + 401 处理', () => {
  const originalAdapter = http.defaults.adapter

  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    http.defaults.adapter = originalAdapter
  })

  it('请求拦截器统一携带 Token', async () => {
    localStorage.setItem('token', 'mock-token-demo')

    let capturedConfig = null
    const interceptor = http.interceptors.request.use((config) => {
      capturedConfig = config
      return config
    })

    // adapter mock：走完整拦截器链，但不发真实网络请求
    http.defaults.adapter = async (config) => ({ data: 'ok', status: 200, config })
    const resp = await http.get('/test')

    http.interceptors.request.eject(interceptor)
    expect(resp.data).toBe('ok')
    // 请求拦截器把 token 写入 headers.Authorization
    expect(capturedConfig.headers.Authorization).toBe('mock-token-demo')
  })

  it('401 响应时清 Token 并跳转登录（走真实拦截器链路）', async () => {
    localStorage.setItem('token', 'mock-token-expired')

    // 用 adapter 模拟服务端返回 401，让 axios 响应拦截器实际触发
    http.defaults.adapter = async () => {
      const error = new Error('Unauthorized')
      error.response = { status: 401, data: {} }
      throw error
    }

    await expect(http.get('/protected')).rejects.toThrow('Unauthorized')
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('handleUnauthorized 清 Token 并触发路由跳登录', () => {
    const pushSpy = vi.spyOn(router, 'push')
    localStorage.setItem('token', 'mock-token-x')
    handleUnauthorized()
    expect(localStorage.getItem('token')).toBeNull()
    expect(pushSpy).toHaveBeenCalledWith('/login')
    pushSpy.mockRestore()
  })
})

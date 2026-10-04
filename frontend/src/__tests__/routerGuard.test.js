import { describe, it, expect, beforeEach } from 'vitest'
import { authGuard } from '../router'

// 关键回归：routerGuard_blocksUnauthenticated_redirectsToLogin
// 课件原文：未登录访问业务页 → 路由守卫重定向 /login
describe('路由守卫：未登录拦截', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('未登录访问工作台时重定向到登录页', () => {
    const result = authGuard({ path: '/workbench' })
    expect(result).toBe('/login')
  })

  it('已登录（有 token）访问工作台放行', () => {
    localStorage.setItem('token', 'mock-token-demo')
    const result = authGuard({ path: '/workbench' })
    expect(result).toBeUndefined()
  })

  it('登录页不拦截', () => {
    const result = authGuard({ path: '/login' })
    expect(result).toBeUndefined()
  })
})

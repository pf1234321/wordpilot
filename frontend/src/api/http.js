import axios from 'axios'
import router from '../router'

// Axios 封装：统一 Token + 401 处理（课件主角一）
// 前端 mock 支线：请求先走 mock 拦截，若 mock 未命中再走真实 axios（预留替换真实后端）
const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 401 处理：清 Token → 跳登录（独立导出以便测试）
// 关键回归：tokenExpired_anyRequest401_redirectsToLogin
export function handleUnauthorized() {
  localStorage.removeItem('token')
  router.push('/login')
}

// 请求拦截器：统一携带 Token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// 响应拦截器：401 → 清 Token → 跳登录
http.interceptors.response.use(
  (resp) => resp,
  (error) => {
    if (error.response?.status === 401) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  }
)

export default http

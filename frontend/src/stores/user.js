import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { mockLogin } from '../mock/mockApi'

// 用户状态：token、登录/登出
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const nickname = ref('')

  const isAuthenticated = computed(() => !!token.value)

  async function login({ username, password }) {
    const { token: tk, nickname: nick } = await mockLogin({ username, password })
    token.value = tk
    nickname.value = nick
    localStorage.setItem('token', tk)
    return tk
  }

  function logout() {
    token.value = ''
    nickname.value = ''
    localStorage.removeItem('token')
  }

  function restore() {
    const tk = localStorage.getItem('token')
    if (tk) {
      token.value = tk
      nickname.value = tk.replace('mock-token-', '')
    }
  }

  return { token, nickname, isAuthenticated, login, logout, restore }
})

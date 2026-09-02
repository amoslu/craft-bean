import { defineStore } from 'pinia'
import { ref } from 'vue'
import { loginApi, meApi, type CurrentUser } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('craftbean_token') || '')
  const user = ref<CurrentUser | null>(null)

  async function login(username: string, password: string) {
    const result = await loginApi(username, password)
    token.value = result.token
    user.value = result.user
    localStorage.setItem('craftbean_token', result.token)
  }

  async function fetchMe() {
    if (token.value) user.value = await meApi()
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('craftbean_token')
  }

  return { token, user, login, fetchMe, logout }
})

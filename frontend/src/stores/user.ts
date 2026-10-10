import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/api/auth'
import { loadToken, saveToken } from '@/api/http'
import type { LoginResponse, Me, RegisterForm } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(loadToken())
  const me = ref<Me | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isVerified = computed(() => me.value?.verifyStatus === 'VERIFIED')

  function applyLogin(res: LoginResponse) {
    token.value = res.token
    saveToken(res.token)
    me.value = res.me
  }

  async function login(email: string, password: string) {
    applyLogin(await api.login(email, password))
  }

  async function register(form: RegisterForm) {
    applyLogin(await api.register(form))
  }

  async function refreshMe() {
    me.value = await api.fetchMe()
  }

  /** 只清本地状态，不请求后端 */
  function clear() {
    token.value = null
    me.value = null
    saveToken(null)
  }

  async function logout() {
    try {
      await api.logout()
    } finally {
      clear()
    }
  }

  return { token, me, isLoggedIn, isVerified, login, register, refreshMe, clear, logout }
})

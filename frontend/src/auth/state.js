import { reactive } from 'vue'
import { api, clearCsrf } from '../api/client.js'

export const authState = reactive({ user: null, ready: false })

window.addEventListener('auth-expired', () => {
  authState.user = null
  authState.ready = true
})

export async function loadCurrentUser() {
  if (authState.ready) return authState.user
  try {
    authState.user = await api('/auth/me')
  } catch (error) {
    if (error.status !== 401) throw error
    authState.user = null
  } finally {
    authState.ready = true
  }
  return authState.user
}

export async function login(username, password) {
  const user = await api('/auth/login', { method: 'POST', body: { username, password } })
  authState.user = user
  authState.ready = true
  return user
}

export async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } finally {
    clearCsrf()
    authState.user = null
    authState.ready = true
  }
}

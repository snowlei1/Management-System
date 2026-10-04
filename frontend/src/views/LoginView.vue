<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../auth/state.js'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await login(username.value.trim(), password.value)
    const destination = route.query.redirect
    await router.replace(typeof destination === 'string' && destination.startsWith('/')
      && !destination.startsWith('//') ? destination : '/')
  } catch (exception) {
    error.value = exception.message || '登录失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="brand-mark">思政</div>
      <p class="eyebrow">课程思政教学资源管理系统</p>
      <h1>登录系统</h1>
      <p class="muted">请使用管理员、教师或学生账号登录。</p>
      <form @submit.prevent="submit">
        <label for="username">账号</label>
        <input id="username" v-model="username" autocomplete="username" maxlength="64" required autofocus />
        <label for="password">密码</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <button class="primary full" type="submit" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button>
      </form>
      <p class="login-foot">当前已开放认证、用户与基础数据管理，按角色展示入口；教学资源等业务将按设计逐步实现。</p>
    </section>
  </main>
</template>

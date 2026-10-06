<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../auth/state.js'
import AppIcon from '../components/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return
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
    <section class="login-intro">
      <div class="brand-mark"><AppIcon name="school" :size="28" /></div>
      <p class="eyebrow">课程 · 资源 · 思政育人</p>
      <h1>课程思政<br />教学资源管理系统</h1>
      <p>面向课程资源建设、审核共享与思政元素组织的教学资源平台</p>
      <div class="login-art" aria-hidden="true"><AppIcon name="course" :size="88" /><AppIcon name="school" :size="72" /><AppIcon name="file" :size="46" /></div>
      <div class="login-caption">教师建设 / 管理员审核 / 学生使用</div>
    </section>
    <section class="login-card">
      <div class="brand-mark">思政</div>
      <p class="eyebrow">教学资源平台</p>
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
      <p class="login-foot">系统内部共享使用。学生仅可查阅已发布资源，不开放上传或审核。</p>
    </section>
  </main>
</template>

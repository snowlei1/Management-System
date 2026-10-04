<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authState, logout } from '../auth/state.js'

const router = useRouter()
const busy = ref(false)
const error = ref('')
const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }

async function signOut() {
  busy.value = true
  error.value = ''
  try {
    await logout()
    await router.replace('/login')
  } catch {
    error.value = '退出请求未完成，请重新登录前不要继续使用本页面。'
    await router.replace('/login')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand"><span class="brand-symbol">思</span><div>课程思政<br /><small>教学资源管理</small></div></div>
      <nav aria-label="主导航">
        <RouterLink to="/" exact-active-class="active">首页</RouterLink>
        <RouterLink v-if="authState.user?.role === 'ADMIN'" to="/users" active-class="active">用户管理</RouterLink>
        <RouterLink to="/profile" active-class="active">个人信息</RouterLink>
      </nav>
      <p class="sidebar-note">当前阶段仅开放已实现功能</p>
    </aside>
    <div class="app-main">
      <header class="topbar">
        <div class="topbar-title">基于SpringBoot的课程思政教学资源管理系统</div>
        <div class="account-area">
          <span>{{ authState.user?.displayName }} <small>· {{ roleNames[authState.user?.role] }}</small></span>
          <button type="button" class="text-button" :disabled="busy" @click="signOut">退出登录</button>
        </div>
      </header>
      <main class="content"><p v-if="error" class="message error">{{ error }}</p><RouterView /></main>
    </div>
  </div>
</template>

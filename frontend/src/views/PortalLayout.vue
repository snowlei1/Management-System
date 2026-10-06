<script setup>
import { nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, logout } from '../auth/state.js'
import AppIcon from '../components/AppIcon.vue'

const route = useRoute(), router = useRouter()
const drawer = ref(false), busy = ref(false), menuButton = ref(null), drawerPanel = ref(null)
const links = [
  ['/', '首页'], ['/resources', '资源中心'], ['/course-resources', '课程资源'],
  ['/ideological-topics', '思政专题'], ['/favorites', '我的收藏'],
  ['/history/browse', '最近浏览'], ['/history/downloads', '下载记录'],
]
function active(path) { return route.path === path || path !== '/' && route.path.startsWith(path + '/') }
async function openMenu() { drawer.value = true; await nextTick(); drawerPanel.value?.querySelector('button')?.focus() }
async function closeMenu() { drawer.value = false; await nextTick(); menuButton.value?.focus() }
function containFocus(event) {
  if (event.key === 'Escape') { event.preventDefault(); closeMenu(); return }
  if (event.key !== 'Tab') return
  const controls = [...event.currentTarget.querySelectorAll('a,button:not(:disabled)')]
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(() => route.fullPath, () => { drawer.value = false })
async function signOut() {
  if (busy.value) return
  busy.value = true
  try { await logout() }
  catch { window.dispatchEvent(new CustomEvent('app-toast', { detail: { type: 'error', message: '退出未完成，请重新登录前不要继续使用页面。' } })) }
  finally { busy.value = false; await router.replace('/login') }
}
</script>

<template>
  <div class="portal-shell">
    <header class="portal-header">
      <div class="portal-header-inner">
        <RouterLink to="/" class="portal-brand"><span class="brand-symbol"><AppIcon name="school" :size="26"/></span><span>课程思政<small>教学资源平台</small></span></RouterLink>
        <nav class="portal-desktop-nav" aria-label="学生门户导航"><RouterLink v-for="[path,label] in links" :key="path" :to="path" :class="{ active:active(path) }" :aria-current="active(path)?'page':undefined">{{label}}</RouterLink></nav>
        <div class="portal-account"><RouterLink to="/profile" class="portal-profile" aria-label="个人信息"><span class="avatar">{{authState.user?.displayName?.slice(0,1)||'用'}}</span><span class="portal-user-name">{{authState.user?.displayName}}<small>学生</small></span></RouterLink><button class="icon-button" :disabled="busy" aria-label="退出登录" @click="signOut"><AppIcon name="logout"/></button><button ref="menuButton" class="icon-button portal-menu-button" aria-label="打开门户导航" :aria-expanded="drawer" aria-controls="student-portal-menu" @click="openMenu"><AppIcon name="menu"/></button></div>
      </div>
    </header>
    <template v-if="drawer">
      <button class="portal-menu-overlay" tabindex="-1" aria-label="关闭门户导航" @click="closeMenu"/>
      <aside id="student-portal-menu" ref="drawerPanel" class="portal-menu-drawer" role="dialog" aria-modal="true" aria-label="学生门户菜单" @keydown="containFocus">
        <div class="portal-menu-heading"><strong>教学资源门户</strong><button class="icon-button" aria-label="关闭门户导航" @click="closeMenu"><AppIcon name="close"/></button></div>
        <nav aria-label="学生门户移动导航"><RouterLink v-for="[path,label] in links" :key="path" :to="path" :class="{ active:active(path) }" :aria-current="active(path)?'page':undefined">{{label}}<AppIcon name="chevron" :size="16"/></RouterLink><RouterLink to="/profile" :class="{ active:active('/profile') }">个人信息<AppIcon name="user" :size="16"/></RouterLink></nav>
      </aside>
    </template>
    <main class="portal-content"><RouterView/></main>
    <footer class="app-footer">课程思政教学资源平台 · 本地开发与演示环境</footer>
  </div>
</template>

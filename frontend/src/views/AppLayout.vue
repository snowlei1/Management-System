<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, logout } from '../auth/state.js'
import AppIcon from '../components/AppIcon.vue'
const router = useRouter(), route = useRoute(), busy = ref(false), collapsed = ref(false), drawer = ref(false)
const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
const item = (path, label, icon) => ({ path, label, icon })
const groups = computed(() => {
  const common = { label: '工作空间', items: [item('/', authState.user?.role === 'STUDENT' ? '资源门户' : '工作台', 'home')] }
  if (authState.user?.role === 'ADMIN') return [common,
    { label: '基础管理', items: [item('/users', '用户管理', 'users'), item('/courses', '课程管理', 'course'), item('/ideological-elements', '课程思政元素', 'tags'), item('/resource-categories', '资源分类', 'category')] },
    { label: '资源治理', items: [item('/resource-reviews', '资源审核', 'audit'), item('/published-resources', '已发布资源台账', 'file'), item('/statistics', '统计概览', 'chart')] },
    { label: '我的账户', items: [item('/profile', '个人信息', 'user')] }]
  return [common, ...(authState.user?.role === 'TEACHER' ? [{ label: '资源建设', items: [item('/my-resources', '我的资源', 'file'), item('/my-resources/new', '新建教学资源', 'upload')] }] : []),
    { label: '资源发现', items: [item('/resources', '资源中心', 'category'), item('/course-resources', '课程资源', 'course'), item('/ideological-topics', '思政元素专题', 'tags')] },
    { label: '我的使用', items: [item('/favorites', '我的收藏', 'favorite'), item('/history/browse', '最近浏览', 'history'), item('/history/downloads', '下载记录', 'download'), item('/profile', '个人信息', 'user')] }]
})
const title = computed(() => route.meta.title || groups.value.flatMap(g => g.items).filter(i => i.path === route.path || i.path !== '/' && route.path.startsWith(i.path + '/')).sort((a,b) => b.path.length - a.path.length)[0]?.label || '资源详情')
const parent = computed(() => groups.value.find(g => g.items.some(i => i.path !== '/' && route.path.startsWith(i.path)))?.label || '工作空间')
function active(i) { return route.path === i.path || i.path !== '/' && i.path !== '/my-resources' && route.path.startsWith(i.path + '/') || i.path === '/my-resources' && route.path.startsWith('/my-resources/') && route.path !== '/my-resources/new' }
watch(() => route.fullPath, () => { drawer.value = false })
async function signOut() { if (busy.value) return; busy.value = true; try { await logout() } catch { window.dispatchEvent(new CustomEvent('app-toast', { detail: { type: 'error', message: '退出未完成，请重新登录前不要继续使用页面。' } })) } finally { busy.value = false; await router.replace('/login') } }
</script>

<template><div class="app-shell" :class="{ 'sidebar-collapsed': collapsed, 'drawer-open': drawer }"><button v-if="drawer" class="drawer-overlay" aria-label="关闭导航" @click="drawer = false"/>
  <aside class="sidebar"><RouterLink to="/" class="sidebar-brand"><span class="brand-symbol"><AppIcon name="school" :size="27"/></span><span class="brand-text">课程思政<small>教学资源平台</small></span></RouterLink><nav aria-label="主导航"><div v-for="group in groups" :key="group.label" class="nav-group"><span class="nav-group-title">{{ group.label }}</span><RouterLink v-for="i in group.items" :key="i.path" :to="i.path" :title="i.label" :class="{ active: active(i) }"><AppIcon :name="i.icon"/><span class="nav-label">{{ i.label }}</span></RouterLink></div></nav><div class="sidebar-bottom"><span class="badge role-badge">{{ roleNames[authState.user?.role] }}</span><small class="sidebar-note">建设 · 审核 · 共享 · 使用</small></div></aside>
  <div class="app-main"><header class="topbar"><div class="topbar-leading"><button class="icon-button desktop-toggle" :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'" @click="collapsed = !collapsed"><AppIcon :name="collapsed ? 'expand' : 'collapse'"/></button><button class="icon-button mobile-toggle" aria-label="打开导航" @click="drawer = !drawer"><AppIcon name="menu"/></button><div><div class="breadcrumb"><RouterLink to="/">首页</RouterLink><AppIcon name="chevron" :size="13"/><span>{{ parent }}</span><AppIcon name="chevron" :size="13"/><span>{{ title }}</span></div><strong class="topbar-title">{{ title }}</strong></div></div><div class="account-area"><span class="avatar">{{ authState.user?.displayName?.slice(0, 1) || '用' }}</span><div><strong>{{ authState.user?.displayName }}</strong><small>{{ roleNames[authState.user?.role] }}</small></div><button class="icon-button" :disabled="busy" aria-label="退出登录" @click="signOut"><AppIcon name="logout"/></button></div></header><main class="content"><RouterView/></main><footer class="app-footer">课程思政教学资源平台 · 本地开发与演示环境</footer></div></div></template>

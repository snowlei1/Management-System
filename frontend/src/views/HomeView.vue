<script setup>
import { authState } from '../auth/state.js'

const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
</script>

<template>
  <section class="page-heading"><p class="eyebrow">系统首页</p><h1>欢迎，{{ authState.user?.displayName }}</h1>
    <p class="muted">当前身份：{{ roleNames[authState.user?.role] }}。当前已实现认证权限、基础数据、资源建设、审核发布与资源使用。</p>
  </section>
  <section class="card">
    <h2>当前可用功能</h2>
    <p v-if="authState.user?.role === 'ADMIN'">你可以维护账号和基础数据，审核待审核资源、追溯审核历史，并在统计概览查看资源分布及累计使用事件。</p>
    <p v-else-if="authState.user?.role === 'TEACHER'">“我的资源”用于本人资源建设与审核进度；“资源中心”用于检索、预览、下载和收藏已发布资源。待审核和已发布内容不能直接编辑。</p>
    <p v-else>你可以在资源中心检索、预览、下载和收藏已发布教学资源，并查看我的收藏；不能上传或审核资源。</p>
  </section>
</template>

<script setup>
import { authState } from '../auth/state.js'

const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
</script>

<template>
  <section class="page-heading"><p class="eyebrow">系统首页</p><h1>欢迎，{{ authState.user?.displayName }}</h1>
    <p class="muted">当前身份：{{ roleNames[authState.user?.role] }}。本阶段已实现账号登录、角色权限和用户管理。</p>
  </section>
  <section class="card">
    <h2>当前可用功能</h2>
    <p v-if="authState.user?.role === 'ADMIN'">你可以进入用户管理，查询、新增和维护教师及学生账号。</p>
    <p v-else>你可以查看自己的账号信息。课程与教学资源等功能将在后续阶段开放。</p>
  </section>
</template>

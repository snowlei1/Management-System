<script setup>
import { authState } from '../auth/state.js'
const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
const statusNames = { ACTIVE: '启用', DISABLED: '停用' }
</script>

<template>
  <section class="page-heading"><p class="eyebrow">我的账号</p><h1>个人信息</h1></section>
  <section class="card profile-card">
    <div><span>账号</span><strong>{{ authState.user?.username }}</strong></div>
    <div><span>姓名</span><strong>{{ authState.user?.displayName }}</strong></div>
    <div><span>角色</span><strong>{{ roleNames[authState.user?.role] }}</strong></div>
    <div><span>状态</span><strong>{{ statusNames[authState.user?.status] }}</strong></div>
  </section>
</template>

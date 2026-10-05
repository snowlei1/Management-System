<script setup>
import { authState } from '../auth/state.js'

const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
</script>

<template>
  <section class="page-heading"><p class="eyebrow">系统首页</p><h1>欢迎，{{ authState.user?.displayName }}</h1>
    <p class="muted">当前身份：{{ roleNames[authState.user?.role] }}。当前已实现认证权限、基础数据、资源建设与审核发布。</p>
  </section>
  <section class="card">
    <h2>当前可用功能</h2>
    <p v-if="authState.user?.role === 'ADMIN'">你可以维护账号和基础数据，并查看审核附件、通过或驳回待审核资源，追溯审核历史。</p>
    <p v-else-if="authState.user?.role === 'TEACHER'">你可以创建、编辑本人草稿及驳回资源，提交审核、查看进度与原因；待审核和已发布内容不能直接编辑。</p>
    <p v-else>你可以查看自己的账号信息。学生资源中心将在资源审核和发布功能完成后开放。</p>
  </section>
</template>

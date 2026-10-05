<script setup>
import { authState } from '../auth/state.js'

const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
</script>

<template>
  <section class="page-heading"><p class="eyebrow">系统首页</p><h1>欢迎，{{ authState.user?.displayName }}</h1>
    <p class="muted">当前身份：{{ roleNames[authState.user?.role] }}。当前已实现认证权限、基础数据及教师资源草稿管理。</p>
  </section>
  <section class="card">
    <h2>当前可用功能</h2>
    <p v-if="authState.user?.role === 'ADMIN'">你可以维护教师和学生账号，以及课程、课程思政元素和资源分类。基础数据使用启用/停用管理，不进行物理删除。</p>
    <p v-else-if="authState.user?.role === 'TEACHER'">你可以创建和编辑自己的资源草稿，上传教学文件并选择课程、分类和思政元素。本阶段尚未开放提交审核和发布。</p>
    <p v-else>你可以查看自己的账号信息。学生资源中心将在资源审核和发布功能完成后开放。</p>
  </section>
</template>

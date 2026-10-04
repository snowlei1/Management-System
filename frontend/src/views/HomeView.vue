<script setup>
import { authState } from '../auth/state.js'

const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
</script>

<template>
  <section class="page-heading"><p class="eyebrow">系统首页</p><h1>欢迎，{{ authState.user?.displayName }}</h1>
    <p class="muted">当前身份：{{ roleNames[authState.user?.role] }}。当前已实现认证权限、用户管理及基础数据管理。</p>
  </section>
  <section class="card">
    <h2>当前可用功能</h2>
    <p v-if="authState.user?.role === 'ADMIN'">你可以维护教师和学生账号，以及课程、课程思政元素和资源分类。基础数据使用启用/停用管理，不进行物理删除。</p>
    <p v-else>你可以查看自己的账号信息。启用的课程、思政元素及分类已提供只读选择接口；教学资源业务页面将在后续阶段开放。</p>
  </section>
</template>

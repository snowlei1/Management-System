<script setup>
import { authState } from '../auth/state.js'
import { defineAsyncComponent } from 'vue'
const AdminDashboard = defineAsyncComponent(() => import('./AdminDashboard.vue'))
const TeacherDashboard = defineAsyncComponent(() => import('./TeacherDashboard.vue'))
const StudentPortal = defineAsyncComponent(() => import('./StudentPortal.vue'))
</script>

<template>
  <AdminDashboard v-if="authState.user?.role === 'ADMIN'"/>
  <TeacherDashboard v-else-if="authState.user?.role === 'TEACHER'"/>
  <StudentPortal v-else/>
</template>

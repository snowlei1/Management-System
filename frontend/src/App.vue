<script setup>
import { onMounted, ref } from 'vue'
import { getSystemStatus } from './api/system.js'

const status = ref('正在检查连接…')
const detail = ref('')
const checking = ref(false)

async function checkConnection() {
  checking.value = true
  status.value = '正在检查连接…'
  detail.value = ''
  try {
    const result = await getSystemStatus()
    status.value = '前后端与 MySQL 通信正常'
    detail.value = `后端：${result.service} · 数据库：${result.database}`
  } catch (error) {
    status.value = '连接检查未通过'
    detail.value = error instanceof Error ? error.message : '未知错误'
  } finally {
    checking.value = false
  }
}

onMounted(checkConnection)
</script>

<template>
  <main class="page">
    <section class="panel">
      <p class="eyebrow">系统设计与实现 · 项目骨架</p>
      <h1>课程思政教学资源管理系统</h1>
      <p class="intro">当前页面用于检查 Vue 前端、Spring Boot 接口与 MySQL 数据库之间的基础通信。</p>
      <div class="status" role="status" aria-live="polite">
        <strong>{{ status }}</strong>
        <span v-if="detail">{{ detail }}</span>
      </div>
      <button type="button" :disabled="checking" @click="checkConnection">重新检查</button>
    </section>
  </main>
</template>

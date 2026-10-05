<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStatistics } from '../api/statistics.js'
import DistributionTable from '../components/DistributionTable.vue'

const data = ref(null)
const busy = ref(false)
const error = ref('')
const loadedAt = ref('')
const cards = computed(() => data.value ? [
  ['用户总数', data.value.users.total], ['管理员', data.value.users.admin], ['教师', data.value.users.teacher],
  ['学生', data.value.users.student], ['启用用户', data.value.users.active], ['停用用户', data.value.users.disabled],
  ['课程总数', data.value.courses.total], ['启用课程', data.value.courses.active],
  ['思政元素总数', data.value.elements.total], ['启用思政元素', data.value.elements.active],
  ['资源分类总数', data.value.categories.total], ['启用资源分类', data.value.categories.active],
] : [])
async function load() {
  busy.value = true
  error.value = ''
  data.value = null
  try {
    data.value = await getStatistics()
    loadedAt.value = new Date().toLocaleString('zh-CN')
  } catch (e) { error.value = e.message || '统计加载失败，请重试' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-heading heading-row statistics-heading"><div><p class="eyebrow">管理员 · 描述性统计</p><h1>统计概览</h1>
    <p class="muted">当前展示本地开发/测试库数据，不代表真实教学使用效果。全量累计统计，不含推荐、预测或综合评分。</p>
  </div><button class="secondary" :disabled="busy" @click="load">{{ busy ? '加载中…' : '刷新统计' }}</button></section>
  <p v-if="error" class="message error" role="alert">{{ error }}</p>
  <p v-if="busy" class="card" role="status">正在读取统计数据…</p>
  <template v-if="data">
    <p class="muted">本次加载时间：{{ loadedAt }}。各项数据来自同一数据库只读快照；后续操作请刷新查看。</p>
    <div class="statistics-grid"><section v-for="[label, value] in cards" :key="label" class="card statistic-card">
      <span>{{ label }}</span><strong>{{ value }}</strong></section></div>
    <section class="card statistics-section"><h2>教学资源状态</h2><p>当前资源排除软删除。已发布资源须为 APPROVED、未删除且发布时间非空。</p>
      <div class="statistics-grid">
        <div v-for="[label, value] in [['当前资源总数', data.resources.total], ['草稿 DRAFT', data.resources.draft],
          ['待审核 PENDING', data.resources.pending], ['驳回 REJECTED', data.resources.rejected],
          ['已发布 APPROVED', data.resources.approved], ['软删除（独立历史数）', data.resources.deleted]]" :key="label" class="statistic-card">
          <span>{{ label }}</span><strong>{{ value }}</strong></div>
      </div>
    </section>
    <section class="card statistics-section"><h2>使用情况（累计）</h2>
      <div class="statistics-grid"><div class="statistic-card"><span>浏览访问事件数</span><strong>{{ data.usage.browseEvents }}</strong></div>
        <div class="statistic-card"><span>下载请求事件数</span><strong>{{ data.usage.downloadRequests }}</strong></div>
        <div class="statistic-card"><span>当前有效公开收藏关系</span><strong>{{ data.usage.currentFavorites }}</strong></div></div>
      <p class="muted">浏览/下载包含软删除资源的历史事件。浏览是成功详情 GET 次数，不是访问人数；下载是校验后准备文件响应的请求数，不保证客户端完整接收。收藏仅计 active=TRUE 且资源仍已发布的关系。</p>
    </section>
    <p class="muted">以下分布仅统计已发布资源，包含停用基础数据的历史关联及零资源条目。按资源数降序、ID升序排列；思政元素可多选，因此其关联数合计可能大于已发布资源数。</p>
    <DistributionTable title="按课程分布" :rows="data.courseDistribution" />
    <DistributionTable title="按资源分类分布" :rows="data.categoryDistribution" />
    <DistributionTable title="按课程思政元素关联分布" :rows="data.elementDistribution" />
  </template>
</template>

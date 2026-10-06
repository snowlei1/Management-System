<script setup>
import { computed, onMounted, ref, defineAsyncComponent } from 'vue'
import { getStatistics } from '../api/statistics.js'
import { portalApi } from '../api/portal.js'
import { timeText } from '../api/resourceState.js'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
import AppIcon from '../components/AppIcon.vue'
const StatisticsCharts = defineAsyncComponent(() => import('../components/StatisticsCharts.vue'))
const data = ref(null), pending = ref([]), loading = ref(true), error = ref('')
const cards = computed(() => data.value ? [['用户总数', data.value.users.total, 'users'], ['教师数量', data.value.users.teacher, 'user'], ['学生数量', data.value.users.student, 'school'], ['课程数量', data.value.courses.total, 'course'], ['待审核资源', data.value.resources.pending, 'pending'], ['已发布资源', data.value.resources.approved, 'approved'], ['累计浏览事件', data.value.usage.browseEvents, 'history'], ['累计下载请求', data.value.usage.downloadRequests, 'download']] : [])
onMounted(async () => { try { const [s, p] = await Promise.all([getStatistics(), portalApi.pendingRecent()]); data.value = s; pending.value = p.map(row => row.resource) } catch (e) { error.value = e.message } finally { loading.value = false } })
</script>
<template><section class="page-heading heading-row"><div><p class="eyebrow">管理与资源治理</p><h1>管理员工作台</h1><p class="muted">从基础数据、资源审核到使用概览，集中处理日常管理事项。</p></div><RouterLink class="primary" to="/resource-reviews"><AppIcon name="audit"/>进入资源审核</RouterLink></section><p v-if="error" class="message error" role="alert">{{ error }}</p><LoadingState v-if="loading"/>
<template v-if="data"><div class="metric-grid"><section v-for="[label, value, icon] in cards" :key="label" class="metric-card"><span>{{ label }}</span><strong>{{ value }}</strong><AppIcon :name="icon" :size="28"/></section></div>
<div class="dashboard-split"><section class="card"><div class="section-heading"><h2>待处理事项 <span class="badge state-PENDING">{{ data.resources.pending }} 项待审核</span></h2><RouterLink to="/resource-reviews" class="text-button">查看全部</RouterLink></div><div v-for="r in pending" :key="r.id" class="activity-row"><span class="file-icon"><AppIcon name="file"/></span><div><strong>{{ r.title }}</strong><p>{{ r.teacherName }} · {{ r.courseName }} · {{ timeText(r.pendingSubmittedAt) }}</p></div><RouterLink :to="`/resource-reviews/${r.id}`" class="secondary">审核</RouterLink></div><EmptyState v-if="!pending.length" title="当前没有待审核资源" description="教师提交后将出现在这里。" icon="approved"/></section>
<section class="card quick-panel"><h2>快捷操作</h2><RouterLink v-for="[label, path, icon] in [['用户管理','/users','users'],['新增课程','/courses?create=1','course'],['维护思政元素','/ideological-elements','tags'],['已发布资源台账','/published-resources','file'],['统计概览','/statistics','chart']]" :key="path" :to="path"><AppIcon :name="icon"/><span>{{ label }}</span><AppIcon name="chevron" :size="15"/></RouterLink><p class="field-hint">当前数据为本地开发/测试数据，不代表真实教学效果。</p></section></div><StatisticsCharts :data="data"/></template></template>

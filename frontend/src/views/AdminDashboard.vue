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
const cards = computed(() => data.value ? [['待审核资源', data.value.resources.pending, 'pending'], ['已发布资源', data.value.resources.approved, 'approved'], ['用户总数', data.value.users.total, 'users'], ['课程数量', data.value.courses.total, 'course']] : [])
const secondary = computed(() => data.value ? [['教师数量',data.value.users.teacher],['学生数量',data.value.users.student],['累计浏览事件',data.value.usage.browseEvents],['累计下载请求',data.value.usage.downloadRequests]] : [])
onMounted(async () => { try { const [s, p] = await Promise.all([getStatistics(), portalApi.pendingRecent()]); data.value = s; pending.value = p.map(row => row.resource) } catch (e) { error.value = e.message } finally { loading.value = false } })
</script>
<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">资源治理 / 管理工作台</p><h1>管理员工作台</h1><p class="muted">处理资源审核与基础数据维护，查阅资源使用概览。</p></div><RouterLink class="primary" to="/resource-reviews"><AppIcon name="audit"/>进入资源审核</RouterLink></section>
  <p v-if="error" class="message error" role="alert">{{ error }}</p><LoadingState v-if="loading"/>
  <template v-if="data">
    <section class="admin-overview" aria-label="资源治理主概览"><div v-for="[label,value,icon] in cards" :key="label" :class="{ 'overview-priority':icon==='pending' }"><span>{{label}}</span><strong>{{value}}</strong><AppIcon :name="icon" :size="21"/></div></section>
    <div class="admin-indicator-band"><div v-for="[label,value] in secondary" :key="label"><span>{{label}}</span><strong>{{value}}</strong></div></div>
    <div class="dashboard-split admin-governance">
      <section class="governance-queue"><div class="section-heading"><h2>待审核事项 <span class="badge state-PENDING">{{data.resources.pending}} 项</span></h2><RouterLink to="/resource-reviews" class="text-button">查看全部</RouterLink></div>
        <div v-for="r in pending" :key="r.id" class="activity-row"><span class="file-icon"><AppIcon name="file"/></span><div><strong>{{r.title}}</strong><p>{{r.teacherName}} · {{r.courseName}} · {{timeText(r.pendingSubmittedAt)}}</p></div><RouterLink :to="`/resource-reviews/${r.id}`" class="secondary">审核</RouterLink></div>
        <EmptyState v-if="!pending.length" title="当前没有待审核资源" description="教师提交后将出现在这里。" icon="approved"/>
      </section>
      <StatisticsCharts :data="data" :indexes="[0]"/>
    </div>
    <div class="admin-shortcuts"><h2>基础管理</h2><RouterLink v-for="[label,path,icon] in [['用户管理','/users','users'],['新增课程','/courses?create=1','course'],['思政元素','/ideological-elements','tags'],['已发布台账','/published-resources','file'],['统计概览','/statistics','chart']]" :key="path" :to="path"><AppIcon :name="icon" :size="16"/>{{label}}</RouterLink></div>
    <StatisticsCharts class="admin-distributions" :data="data" :indexes="[1,2,3]"/>
    <p class="field-hint">当前数字来自本地开发/测试库，不代表真实教学效果。</p>
  </template>
</template>

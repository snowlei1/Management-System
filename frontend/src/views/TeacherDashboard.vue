<script setup>
import { onMounted, ref } from 'vue'
import { portalApi } from '../api/portal.js'
import { timeText, resourceStates } from '../api/resourceState.js'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
import AppIcon from '../components/AppIcon.vue'
const data = ref(null), loading = ref(true), error = ref('')
onMounted(async () => { try { data.value = await portalApi.dashboard() } catch (e) { error.value = e.message } finally { loading.value = false } })
</script>
<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">资源建设 / 教师工作台</p><h1>教师工作台</h1><p class="muted">跟进本人资源进度、处理审核意见，查阅已发布资源的使用记录。</p></div><RouterLink class="primary" to="/my-resources/new"><AppIcon name="upload"/>新建教学资源</RouterLink></section>
  <p v-if="error" class="message error" role="alert">{{error}}</p><LoadingState v-if="loading"/>
  <template v-if="data">
    <nav class="teacher-progress-bar" aria-label="本人资源状态"><RouterLink v-for="[code,label] in [['DRAFT','草稿'],['PENDING','审核中'],['REJECTED','已驳回'],['APPROVED','已发布']]" :key="code" :to="`/my-resources?status=${code}`" :class="{ 'progress-attention':code==='REJECTED' && data.counts.REJECTED>0 }"><span>{{label}}</span><strong>{{data.counts[code]}}</strong></RouterLink></nav>
    <div class="teacher-main teacher-workspace">
      <div>
        <section class="work-section"><div class="section-heading"><h2>最近编辑</h2><RouterLink to="/my-resources" class="text-button">全部资源</RouterLink></div>
          <div v-for="row in data.recent" :key="row.resource.id" class="activity-row"><span class="file-icon"><AppIcon name="file"/></span><div><RouterLink :to="`/my-resources/${row.resource.id}`"><strong>{{row.resource.title}}</strong></RouterLink><p>{{row.resource.courseName}} · {{timeText(row.resource.updatedAt)}}</p></div><span class="badge" :class="`state-${row.resource.status}`">{{resourceStates[row.resource.status]}}</span></div>
          <EmptyState v-if="!data.recent.length" title="暂无资源" description="可从新建教学资源开始。"/>
        </section>
        <section class="work-section"><div class="section-heading"><h2>已发布资源使用情况</h2><span class="muted">最近5份 · 描述性事件统计</span></div>
          <div class="table-wrap"><table><thead><tr><th>资源标题</th><th>课程</th><th>浏览事件</th><th>下载请求</th><th>当前收藏</th><th>操作</th></tr></thead><tbody><tr v-for="row in data.published" :key="row.resource.id"><td class="base-name">{{row.resource.title}}</td><td>{{row.resource.courseName}}</td><td>{{row.usage.browseEvents}}</td><td>{{row.usage.downloadRequests}}</td><td>{{row.usage.currentFavorites}}</td><td><RouterLink class="link-button" :to="`/resources/${row.resource.id}`">查看资源</RouterLink></td></tr></tbody></table></div><EmptyState v-if="!data.published.length" title="暂无已发布资源" description="审核通过后展示使用次数，不做效果评价。"/>
        </section>
      </div>
      <aside class="teacher-actions"><section class="teacher-revisions"><div class="section-heading"><h2>待处理驳回</h2><span class="badge state-REJECTED">{{data.counts.REJECTED}} 份</span></div>
        <div v-for="row in data.rejected" :key="row.resource.id" class="rejection-item"><strong>{{row.resource.title}}</strong><p>{{row.latestAudit?.reason || '请打开资源详情查看审核意见。'}}</p><small>{{timeText(row.latestAudit?.auditedAt)}}</small><RouterLink class="link-button" :to="`/my-resources/${row.resource.id}/edit`">编辑资源</RouterLink></div>
        <EmptyState v-if="!data.rejected.length" title="暂无待修改资源" description="驳回后可在此查看原因并修改重提。" icon="approved"/>
        </section><div class="section-heading"><h2>资源建设入口</h2></div><div class="teacher-action-links"><RouterLink v-for="[label,path,icon] in [['新建教学资源','/my-resources/new','upload'],['资源中心','/resources','category'],['我的资源','/my-resources','file']]" :key="path" :to="path"><AppIcon :name="icon"/>{{label}}<AppIcon name="arrow" :size="16"/></RouterLink></div>
      </aside>
    </div>
  </template>
</template>

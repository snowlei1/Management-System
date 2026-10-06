<script setup>
import { timeText } from '../api/resourceState.js'
defineProps({ records: { type: Array, default: () => [] } })
</script>
<template>
  <section class="audit-history"><h2>审核历史</h2><p v-if="!records.length" class="muted">尚无审核结论。</p>
    <p class="field-hint">按真实审核轮次展示结论；不补造教师修改或提交时间。</p>
    <ol v-if="records.length" class="audit-timeline"><li v-for="a in records" :key="a.id" :class="{ 'audit-rejected': a.decision === 'REJECT' }"><span class="audit-round">第 {{ a.submissionNo }} 轮</span><strong>{{ a.decision === 'APPROVE' ? '审核通过并发布' : '审核驳回' }}</strong><p>审核人：{{ a.reviewerName }} · {{ timeText(a.auditedAt) }}</p><p v-if="a.reason" class="preserve-lines audit-reason">驳回原因：{{ a.reason }}</p></li></ol>
  </section>
</template>

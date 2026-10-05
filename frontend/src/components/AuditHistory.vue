<script setup>
import { timeText } from '../api/resourceState.js'
defineProps({ records: { type: Array, default: () => [] } })
</script>
<template>
  <section class="audit-history"><h2>审核历史</h2><p v-if="!records.length" class="muted">尚无审核结论。</p>
    <ol v-else class="audit-timeline"><li v-for="a in records" :key="a.id"><strong>第 {{ a.submissionNo }} 轮 · {{ a.decision === 'APPROVE' ? '审核通过' : '审核驳回' }}</strong><p>{{ a.reviewerName }} · {{ timeText(a.auditedAt) }}</p><p v-if="a.reason" class="preserve-lines">驳回原因：{{ a.reason }}</p></li></ol>
  </section>
</template>

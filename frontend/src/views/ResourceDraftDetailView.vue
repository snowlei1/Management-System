<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { resourceDraftApi } from '../api/resources.js'
import { editableResource } from '../api/resourceState.js'
import ResourceInformation from '../components/ResourceInformation.vue'
import AuditHistory from '../components/AuditHistory.vue'
import ResourceSubmitDialog from '../components/ResourceSubmitDialog.vue'
const route = useRoute(), resource = ref(null), error = ref(''), loading = ref(false), confirming = ref(false)
let sequence = 0
watch(() => route.params.id, async id => {
  const current = ++sequence; loading.value = true; error.value = ''; resource.value = null; confirming.value = false
  try { const detail = await resourceDraftApi.detail(id); if (current === sequence) resource.value = detail }
  catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}, { immediate: true })
function submitted(r) { resource.value = r; confirming.value = false }
</script>

<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">教学资源管理</p><h1>资源详情</h1><p class="muted">本人资源、提交进度与完整审核结论。</p></div><RouterLink class="secondary" to="/my-resources">返回列表</RouterLink></section>
  <p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="loading" class="muted">正在加载…</p>
  <section v-if="resource" class="card resource-detail-card">
    <p v-if="route.query.saved && editableResource(resource)" class="message success" role="status">资源已保存；保存不会自动提交审核。</p>
    <ResourceInformation :resource="resource" />
    <p v-if="resource.status === 'PENDING'" class="message success">审核中，不能编辑、删除或再次提交。</p>
    <p v-if="resource.status === 'APPROVED'" class="message success">已审核通过并发布；本阶段不开放已发布内容改版。</p>
    <AuditHistory :records="resource.auditRecords" :pending-submission-no="resource.status==='PENDING'?resource.submissionNo:0" :pending-submitted-at="resource.pendingSubmittedAt" />
    <div v-if="editableResource(resource)" class="modal-actions"><RouterLink class="secondary" :to="`/my-resources/${resource.id}/edit`">编辑资源</RouterLink><button class="primary" @click="confirming = true">{{ resource.status === 'REJECTED' ? '重新提交审核' : '提交审核' }}</button></div>
  </section>
  <ResourceSubmitDialog v-if="confirming && resource" :resource="resource" @close="confirming = false" @submitted="submitted" />
</template>

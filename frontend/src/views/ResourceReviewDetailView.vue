<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { resourceReviewApi } from '../api/resources.js'
import ResourceInformation from '../components/ResourceInformation.vue'
import AuditHistory from '../components/AuditHistory.vue'
const ReviewPdfPreview = defineAsyncComponent(() => import('../components/ReviewPdfPreview.vue'))
const route = useRoute(), resource = ref(null), loading = ref(false), busy = ref(false)
const error = ref(''), notice = ref(''), action = ref(''), reason = ref(''), previewing = ref(false)
const previewUrl = ref(''), previewBusy = ref(false)
let previewSequence = 0
function clearPreview() { previewSequence++; if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = ''; previewing.value = false; previewBusy.value = false }
onBeforeUnmount(clearPreview)
async function preview() {
  if (previewing.value) { clearPreview(); return }
  if (previewBusy.value) return
  const current = ++previewSequence, id = resource.value.id
  previewBusy.value = true; error.value = ''
  try {
    // Fetch under the existing Session authorization. A local Blob avoids relaxing DENY framing headers.
    const blob = await resourceReviewApi.attachment(id)
    if (current !== previewSequence) return
    previewUrl.value = URL.createObjectURL(blob); previewing.value = true
  } catch (e) { if (current === previewSequence) error.value = e.message }
  finally { if (current === previewSequence) previewBusy.value = false }
}
const inline = computed(() => ['application/pdf', 'image/png', 'image/jpeg'].includes(resource.value?.fileMimeType))
const attachmentUrl = computed(() => `/api/admin/resource-reviews/${resource.value?.id}/attachment`)
let sequence = 0
watch(() => route.params.id, async id => {
  const current = ++sequence; loading.value = true; error.value = ''; resource.value = null; action.value = ''; clearPreview()
  try { const r = await resourceReviewApi.detail(id); if (current === sequence) resource.value = r }
  catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}, { immediate: true })
function confirm(which) { action.value = which; reason.value = ''; error.value = '' }
async function decide() {
  if (busy.value) return
  if (action.value === 'reject' && (!reason.value.trim() || reason.value.trim().length > 1000)) { error.value = '请填写1—1000个字符的驳回原因'; return }
  busy.value = true; error.value = ''
  try {
    resource.value = action.value === 'approve' ? await resourceReviewApi.approve(resource.value.id, resource.value.submissionNo) : await resourceReviewApi.reject(resource.value.id, resource.value.submissionNo, reason.value.trim())
    action.value = ''; notice.value = resource.value.status === 'APPROVED' ? '审核通过，资源已发布。' : '已驳回，教师可按原因修改后重新提交。'
  } catch (e) {
    error.value = e.message
    if (e.status === 409) { action.value = ''; clearPreview(); resource.value = await resourceReviewApi.detail(route.params.id).catch(() => null) }
  } finally { busy.value = false }
}
</script>
<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">教学资源审核</p><h1>审核详情</h1><p class="muted">请查看附件内容后作出结论；审核历史不覆盖。</p></div><RouterLink class="secondary" to="/resource-reviews">返回审核列表</RouterLink></section>
  <p v-if="error && !action" class="message error" role="alert">{{ error }}</p><p v-if="notice" class="message success" role="status">{{ notice }}</p><p v-if="loading" class="muted">正在加载…</p>
  <section v-if="resource" class="card resource-detail-card"><ResourceInformation :resource="resource" />
    <section class="review-attachment"><h2>审核附件</h2><p>附件读取仅用于管理员审核，不计普通资源下载次数。Office文件请下载后查看。</p>
      <div class="attachment-actions"><button v-if="inline" class="secondary" :disabled="previewBusy" @click="preview">{{ previewBusy ? '读取附件…' : previewing ? '收起附件预览' : '预览审核附件' }}</button><a class="secondary" :href="`${attachmentUrl}?download=true`" target="_blank" rel="noopener">下载审核附件</a></div>
      <ReviewPdfPreview v-if="previewing && resource.fileMimeType === 'application/pdf'" :src="previewUrl" />
      <img v-else-if="previewing" class="review-image" :src="previewUrl" alt="审核教学资源图片" />
    </section>
    <AuditHistory :records="resource.auditRecords" />
    <div v-if="resource.status === 'PENDING'" class="modal-actions"><button class="secondary" @click="confirm('reject')">驳回</button><button class="primary" @click="confirm('approve')">审核通过</button></div>
    <p v-else class="field-hint">当前资源不处于待审核状态，不允许重复审核。</p>
  </section>
  <div v-if="action" class="modal-backdrop" @click.self="!busy && (action = '')"><section class="modal-card" role="dialog" aria-modal="true" :aria-label="action === 'approve' ? '确认审核通过' : '确认审核驳回'">
    <h2>{{ action === 'approve' ? '确认审核通过' : '确认审核驳回' }}</h2><p>{{ action === 'approve' ? '审核通过后资源将进入已发布状态，本阶段不能直接编辑已发布内容。' : '请填写具体原因，供教师修改后重新提交。' }}</p>
    <form @submit.prevent="decide"><template v-if="action === 'reject'"><label for="review-reason">驳回原因</label><textarea id="review-reason" v-model="reason" maxlength="1000" rows="5" required /></template><p v-if="error" class="message error" role="alert">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary" :disabled="busy" @click="action = ''">取消</button><button class="primary" :disabled="busy">{{ busy ? '处理中…' : action === 'approve' ? '确认通过并发布' : '确认驳回' }}</button></div></form>
  </section></div>
</template>

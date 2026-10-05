<script setup>
import { ref } from 'vue'
import { resourceDraftApi } from '../api/resources.js'
defineProps({ resource: { type: Object, required: true } })
const emit = defineEmits(['close', 'submitted'])
const busy = ref(false), error = ref('')
async function submit(id) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { const r = await resourceDraftApi.submit(id); emit('submitted', r) }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <div class="modal-backdrop" @click.self="!busy && emit('close')"><section class="modal-card confirm-card" role="dialog" aria-modal="true" aria-label="确认提交审核">
    <h2>{{ resource.status === 'REJECTED' ? '重新提交审核' : '提交审核' }}</h2>
    <p>“{{ resource.title }}”提交后将进入待审核状态，期间不能编辑、替换附件或删除。须关联至少一个有效思政元素，课程、分类与附件也会由后端重新校验。</p>
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <div class="modal-actions"><button class="secondary" :disabled="busy" @click="emit('close')">取消</button><button class="primary" :disabled="busy" @click="submit(resource.id)">{{ busy ? '提交中…' : '确认提交审核' }}</button></div>
  </section></div>
</template>

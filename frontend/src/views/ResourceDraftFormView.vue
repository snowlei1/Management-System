<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resourceDraftApi } from '../api/resources.js'
import { getCourseOptions, getIdeologicalElementOptions, getResourceCategoryOptions } from '../api/baseData.js'
import { editableResource } from '../api/resourceState.js'
import AppIcon from '../components/AppIcon.vue'
import LoadingState from '../components/LoadingState.vue'

const route = useRoute(), router = useRouter()
const editing = computed(() => route.name === 'resource-edit')
const form = reactive({ title: '', description: '', courseId: '', categoryId: '', elementIds: [] })
const courses = ref([]), categories = ref([]), elements = ref([]), existing = ref(null)
const policy = ref(null), file = ref(null), loading = ref(false), saving = ref(false), error = ref('')
const ready = ref(false), fileInput = ref(null)
const dragging = ref(false)
const accept = computed(() => policy.value?.allowedExtensions.map(e => `.${e}`).join(',') || '')
let sequence = 0
watch(() => route.fullPath, async () => {
  const current = ++sequence
  loading.value = true; error.value = ''; ready.value = false; file.value = null; existing.value = null
  Object.assign(form, { title: '', description: '', courseId: '', categoryId: '', elementIds: [] })
  if (fileInput.value) fileInput.value.value = ''
  try {
    const [cs, ks, es, rules, detail] = await Promise.all([getCourseOptions(), getResourceCategoryOptions(), getIdeologicalElementOptions(), resourceDraftApi.policy(), editing.value ? resourceDraftApi.detail(route.params.id) : null])
    if (current !== sequence) return
    courses.value = cs; categories.value = ks; elements.value = es; policy.value = rules; existing.value = detail
    if (detail) {
      if (!editableResource(detail)) throw new Error('待审核或已通过资源不能编辑，请返回资源详情。')
      Object.assign(form, { title: detail.title, description: detail.description || '', courseId: detail.courseId, categoryId: detail.categoryId, elementIds: detail.elements.map(e => e.id) })
      if (!cs.some(c => c.id === detail.courseId)) courses.value.push({ id: detail.courseId, name: `${detail.courseName}（已停用，请重新选择）`, inactive: true })
      if (!ks.some(c => c.id === detail.categoryId)) categories.value.push({ id: detail.categoryId, name: `${detail.categoryName}（已停用，请重新选择）`, inactive: true })
      for (const e of detail.elements) if (!es.some(item => item.id === e.id)) elements.value.push({ ...e, inactive: true })
    }
    ready.value = true
  } catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}, { immediate: true })
function selectFile(event) { file.value = event.target.files?.[0] || null; error.value = '' }
function dropFile(event) { dragging.value = false; if (!saving.value) { file.value = event.dataTransfer.files?.[0] || null; error.value = '' } }
async function save() {
  if (saving.value || !ready.value) return
  error.value = ''
  const payload = { title: form.title.trim(), description: form.description.trim() || null, courseId: Number(form.courseId), categoryId: Number(form.categoryId), elementIds: [...form.elementIds] }
  if (!payload.title || !payload.courseId || !payload.categoryId) { error.value = '请填写标题、课程和资源分类'; return }
  if (!editing.value && !file.value) { error.value = '请选择资源文件'; return }
  if (file.value) {
    if (!file.value.size || file.value.size > policy.value.maxSizeBytes) { error.value = '文件不能为空，且不能超过上传大小限制'; return }
    const extension = file.value.name.split('.').pop().toLowerCase()
    if (!policy.value.allowedExtensions.includes(extension)) { error.value = '不支持该文件类型'; return }
  }
  saving.value = true
  try {
    const result = editing.value ? await resourceDraftApi.update(route.params.id, payload, file.value) : await resourceDraftApi.create(payload, file.value)
    await router.push({ name: 'resource-detail', params: { id: result.id }, query: { saved: '1' } })
  } catch (e) { error.value = e.message }
  finally { saving.value = false }
}
</script>

<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">教学资源管理</p><h1>{{ editing ? '编辑资源' : '新建资源' }}</h1><p class="muted">草稿可暂不标注思政元素；提交审核时须至少一个有效元素。驳回后保存仍保持驳回状态，需要另行重新提交。</p></div><RouterLink class="secondary" to="/my-resources">返回列表</RouterLink></section>
  <section class="card resource-form-card">
    <LoadingState v-if="loading" /><p v-if="error" class="message error" role="alert">{{ error }}</p>
    <form v-if="ready" @submit.prevent="save">
      <fieldset :disabled="saving" class="resource-fieldset">
        <section class="form-section"><h2>基本信息</h2><p class="field-hint">用明确的标题和简介描述教学材料。</p>
        <label for="draft-title">资源名称 / 标题</label><input id="draft-title" v-model="form.title" maxlength="200" required />
        <label for="draft-description">资源简介（可选）</label><textarea id="draft-description" v-model="form.description" maxlength="2000" rows="4" />
        </section><section class="form-section"><h2>资源归属</h2><p class="field-hint">每份资源归属于一门课程和一个资源分类。</p>
        <div class="resource-grid"><div><label for="draft-course">所属课程</label><select id="draft-course" v-model="form.courseId" required><option value="" disabled>请选择课程</option><option v-for="c in courses" :key="c.id" :value="c.id" :disabled="c.inactive">{{ c.name }}</option></select></div><div><label for="draft-category">资源分类</label><select id="draft-category" v-model="form.categoryId" required><option value="" disabled>请选择分类</option><option v-for="c in categories" :key="c.id" :value="c.id" :disabled="c.inactive">{{ c.name }}</option></select></div></div>
        <p v-if="!courses.length || !categories.length" class="message error">暂无可用课程或分类，请先联系管理员维护基础数据。</p>
        </section><section class="form-section"><h2>课程思政标注</h2><p class="field-hint">允许多选；草稿可以暂不标注，提交审核时必须有至少一个启用元素。</p>
        <fieldset class="element-picker"><legend>课程思政元素（草稿可选，可多选）</legend><label v-for="e in elements" :key="e.id" class="checkbox-label"><input v-model="form.elementIds" type="checkbox" :value="e.id" />{{ e.name }}{{ e.inactive ? '（已停用，请取消该标注）' : '' }}</label><p v-if="!elements.length" class="muted">暂无启用元素，草稿仍可保存。</p></fieldset>
        </section><section class="form-section"><h2>附件</h2>
        <label for="draft-file">{{ editing ? '替换资源文件（可选）' : '资源文件' }}</label><p v-if="existing" class="field-hint">当前附件：{{ existing.fileOriginalName }} · {{ (existing.fileSizeBytes / 1024).toFixed(1) }} KiB。未选择新文件时保留原附件。</p>
        <div class="drop-zone" :class="{ dragging }" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="dropFile"><AppIcon name="upload" :size="30" /><strong>拖入一个文件，或点击选择</strong><input id="draft-file" ref="fileInput" type="file" :accept="accept" :required="!editing && !file" @change="selectFile" /><p>保存时上传文件，不会直接发布。</p></div>
        <p class="field-hint">单文件上限 {{ (policy.maxSizeBytes / 1024 / 1024).toFixed(0) }} MiB；支持 {{ policy.allowedExtensions.join('、').toUpperCase() }}。服务器会再次校验类型、大小和内容特征；不支持视频。</p>
        <div v-if="file" class="selected-file"><AppIcon name="file" :size="28" /><div><strong>{{ file.name }}</strong><small>{{ (file.size / 1024).toFixed(1) }} KiB · 已选择，保存后生效</small></div><button class="secondary" type="button" @click="fileInput.click()">替换文件</button></div>
        </section>
        <div class="modal-actions"><RouterLink class="secondary" to="/my-resources">取消</RouterLink><button class="primary" type="submit">{{ saving ? '保存中…' : existing?.status === 'REJECTED' ? '保存修改' : '保存草稿' }}</button></div>
      </fieldset>
    </form>
  </section>
</template>

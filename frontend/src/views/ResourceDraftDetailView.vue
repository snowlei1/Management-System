<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { resourceDraftApi } from '../api/resources.js'
const route = useRoute(), resource = ref(null), error = ref(''), loading = ref(false)
let sequence = 0
watch(() => route.params.id, async id => {
  const current = ++sequence; loading.value = true; error.value = ''; resource.value = null
  try { const detail = await resourceDraftApi.detail(id); if (current === sequence) resource.value = detail }
  catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}, { immediate: true })
</script>

<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">教学资源草稿</p><h1>资源详情</h1><p class="muted">只有创建教师可查看此草稿；尚未进入审核或发布流程。</p></div><RouterLink class="secondary" to="/my-resources">返回列表</RouterLink></section>
  <p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="loading" class="muted">正在加载…</p>
  <section v-if="resource" class="card resource-detail-card">
    <p v-if="route.query.saved" class="message success" role="status">草稿已保存</p>
    <h2>{{ resource.title }}</h2><span class="badge badge-disabled">草稿 DRAFT</span>
    <dl class="resource-details"><dt>资源简介</dt><dd class="preserve-lines">{{ resource.description || '—' }}</dd><dt>所属课程</dt><dd>{{ resource.courseName }}{{ resource.courseStatus === 'INACTIVE' ? '（已停用）' : '' }}</dd><dt>资源分类</dt><dd>{{ resource.categoryName }}{{ resource.categoryStatus === 'INACTIVE' ? '（已停用）' : '' }}</dd><dt>思政元素</dt><dd>{{ resource.elements.map(e => e.name + (e.status === 'INACTIVE' ? '（已停用）' : '')).join('、') || '尚未标注（草稿允许）' }}</dd><dt>附件原始名称</dt><dd>{{ resource.fileOriginalName }}</dd><dt>附件类型</dt><dd>{{ resource.fileMimeType }}</dd><dt>附件大小</dt><dd>{{ resource.fileSizeBytes }} 字节（{{ (resource.fileSizeBytes / 1024).toFixed(1) }} KiB）</dd><dt>创建时间</dt><dd>{{ resource.createdAt?.replace('T', ' ') }}</dd><dt>更新时间</dt><dd>{{ resource.updatedAt?.replace('T', ' ') }}</dd></dl>
    <p class="field-hint">本阶段只展示附件元数据，不提供尚未实现的预览、下载、收藏或提交审核入口。</p>
    <div class="modal-actions"><RouterLink class="primary" :to="`/my-resources/${resource.id}/edit`">编辑草稿</RouterLink></div>
  </section>
</template>

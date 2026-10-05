<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { publishedResourceApi } from '../api/resources.js'
import { timeText } from '../api/resourceState.js'
const ResourcePdfPreview = defineAsyncComponent(() => import('../components/ReviewPdfPreview.vue'))
const route = useRoute(), resource = ref(null), loading = ref(false), busy = ref(false)
const error = ref(''), notice = ref(''), previewUrl = ref(''), previewBusy = ref(false)
const canPreview = computed(() => ['application/pdf', 'image/png', 'image/jpeg'].includes(resource.value?.fileMimeType))
let sequence = 0, previewSequence = 0
function clearPreview() { previewSequence++; if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = ''; previewBusy.value = false }
onBeforeUnmount(() => { sequence++; clearPreview() })
watch(() => route.params.id, async id => {
  const current = ++sequence; loading.value = true; resource.value = null; error.value = ''; notice.value = ''; clearPreview()
  try { const value = await publishedResourceApi.detail(id); if (current === sequence) resource.value = value }
  catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}, { immediate: true })
async function favorite() {
  if (busy.value) return
  busy.value = true; error.value = ''; const current = sequence, id = resource.value.id
  try { const state = await publishedResourceApi.favorite(id, !resource.value.favorite); if (current === sequence) { resource.value.favorite = state.favorite; notice.value = state.favorite ? '已收藏' : '已取消收藏' } }
  catch (e) { if (current === sequence) error.value = e.message }
  finally { busy.value = false }
}
async function preview() {
  if (previewUrl.value) { clearPreview(); return }
  if (previewBusy.value) return
  const current = ++previewSequence, id = resource.value.id; previewBusy.value = true; error.value = ''
  try { const blob = await publishedResourceApi.preview(id); if (current === previewSequence) previewUrl.value = URL.createObjectURL(blob) }
  catch (e) { if (current === previewSequence) error.value = e.message }
  finally { if (current === previewSequence) previewBusy.value = false }
}
</script>
<template>
  <section class="page-heading heading-row"><div><p class="eyebrow">资源中心</p><h1>资源详情</h1><p class="muted">每次成功打开或刷新详情记一次访问事件；预览与收藏不会重复计数。</p></div><RouterLink class="secondary" to="/resources">返回资源中心</RouterLink></section>
  <p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="notice" class="message success" role="status">{{ notice }}</p><p v-if="loading" class="muted">正在加载…</p>
  <section v-if="resource" class="card resource-detail-card"><h2>{{ resource.title }}</h2><span class="badge badge-active">已发布</span>
    <dl class="resource-details"><dt>资源简介</dt><dd class="preserve-lines">{{ resource.description || '—' }}</dd><dt>所属课程</dt><dd>{{ resource.courseName }}</dd><dt>资源分类</dt><dd>{{ resource.categoryName }}</dd><dt>课程思政元素</dt><dd>{{ resource.elements.map(e => e.name).join('、') }}</dd><dt>发布教师</dt><dd>{{ resource.teacherName }}</dd><dt>发布时间</dt><dd>{{ timeText(resource.publishedAt) }}</dd><dt>文件名称</dt><dd>{{ resource.fileOriginalName }}</dd><dt>文件类型</dt><dd>{{ resource.fileMimeType }}</dd><dt>文件大小</dt><dd>{{ resource.fileSizeBytes }} 字节（{{ (resource.fileSizeBytes / 1024).toFixed(1) }} KiB）</dd><dt>收藏状态</dt><dd>{{ resource.favorite ? '已收藏' : '未收藏' }}</dd></dl>
    <div class="attachment-actions"><button class="secondary" :disabled="busy" @click="favorite">{{ resource.favorite ? '取消收藏' : '收藏' }}</button><button v-if="canPreview" class="secondary" :disabled="previewBusy" @click="preview">{{ previewBusy ? '读取附件…' : previewUrl ? '收起预览' : '预览资源' }}</button><a class="primary" :href="`/api/resources/${resource.id}/download`" :download="resource.fileOriginalName">下载资源</a></div>
    <p v-if="!canPreview" class="field-hint">该格式暂不支持浏览器内预览，请下载后查看。</p>
    <ResourcePdfPreview v-if="previewUrl && resource.fileMimeType === 'application/pdf'" :src="previewUrl" label="资源预览" />
    <img v-else-if="previewUrl" class="review-image" :src="previewUrl" alt="已发布教学资源图片" />
  </section>
</template>

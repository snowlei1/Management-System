<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
GlobalWorkerOptions.workerSrc = workerUrl
const props = defineProps({ src: { type: String, required: true } })
const canvas = ref(null), page = ref(1), pages = ref(0), busy = ref(false), error = ref('')
let document = null, loadingTask = null, renderTask = null, generation = 0
function release() { generation++; renderTask?.cancel(); renderTask = null; loadingTask?.destroy().catch(() => {}); loadingTask = null; document = null; busy.value = false }
async function render(n, current = generation) {
  if (!document || busy.value) return
  busy.value = true; error.value = ''
  try {
    const pdfPage = await document.getPage(n)
    if (current !== generation) return
    await nextTick()
    const unscaled = pdfPage.getViewport({ scale: 1 }), available = Math.max(200, Math.min(860, canvas.value.parentElement.clientWidth))
    const viewport = pdfPage.getViewport({ scale: available / unscaled.width })
    if (viewport.height > 8000) throw new Error('页面尺寸不支持内嵌显示，请下载审核附件查看。')
    canvas.value.width = Math.ceil(viewport.width); canvas.value.height = Math.ceil(viewport.height)
    renderTask = pdfPage.render({ canvasContext: canvas.value.getContext('2d'), viewport })
    await renderTask.promise
    if (current === generation) page.value = n
  } catch (e) { if (current === generation && e.name !== 'RenderingCancelledException') error.value = 'PDF预览失败，请下载审核附件查看。' }
  finally { if (current === generation) { busy.value = false; renderTask = null } }
}
watch(() => props.src, async url => {
  release(); const current = generation; page.value = 1; pages.value = 0; error.value = ''
  loadingTask = getDocument({ url, isEvalSupported: false, enableXfa: false, useSystemFonts: true })
  try {
    const pdf = await loadingTask.promise
    if (current !== generation) return
    document = pdf; pages.value = pdf.numPages; await render(1, current)
  } catch { if (current === generation) error.value = 'PDF无法解析，请下载审核附件查看。' }
}, { immediate: true })
onBeforeUnmount(release)
</script>
<template>
  <div class="pdf-review"><p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="!pages && !error" class="muted">正在解析PDF…</p>
    <div v-if="pages" class="pagination"><span>审核附件 · 第 {{ page }} / {{ pages }} 页</span><div><button class="secondary" :disabled="busy || page <= 1" @click="render(page - 1)">上一页</button><button class="secondary" :disabled="busy || page >= pages" @click="render(page + 1)">下一页</button></div></div>
    <canvas ref="canvas" class="pdf-review-canvas" aria-label="PDF审核材料内容" />
  </div>
</template>

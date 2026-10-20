<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
GlobalWorkerOptions.workerSrc = workerUrl
const props = defineProps({ src: { type: String, required: true }, label: { type: String, default: '审核附件' } })
const canvas = ref(null), page = ref(1), pages = ref(0), busy = ref(false), error = ref('')
const viewer = ref(null), viewportElement = ref(null), zoom = ref(1), targetPage = ref(1), fullscreen = ref(false)
const fullscreenSupported = !!window.document.fullscreenEnabled
let document = null, loadingTask = null, renderTask = null, generation = 0
function release() { generation++; renderTask?.cancel(); renderTask = null; loadingTask?.destroy().catch(() => {}); loadingTask = null; document = null; busy.value = false }
async function render(n, current = generation) {
  if (!document || busy.value) return
  busy.value = true; error.value = ''
  try {
    const pdfPage = await document.getPage(n)
    if (current !== generation) return
    await nextTick()
    const unscaled = pdfPage.getViewport({ scale: 1 }), available = Math.max(180, Math.min(1100, viewportElement.value.clientWidth - 24))
    const viewport = pdfPage.getViewport({ scale: available / unscaled.width * zoom.value })
    if (viewport.height > 8000) throw new Error('页面尺寸不支持内嵌显示，请下载审核附件查看。')
    canvas.value.width = Math.ceil(viewport.width); canvas.value.height = Math.ceil(viewport.height)
    renderTask = pdfPage.render({ canvasContext: canvas.value.getContext('2d'), viewport })
    await renderTask.promise
    if (current === generation) { page.value = n; targetPage.value = n }
  } catch (e) { if (current === generation && e.name !== 'RenderingCancelledException') error.value = 'PDF预览失败，请下载审核附件查看。' }
  finally { if (current === generation) { busy.value = false; renderTask = null } }
}
async function jump() {
  const value = Number(targetPage.value)
  if (!Number.isInteger(value) || value < 1 || value > pages.value) { targetPage.value = page.value; error.value = `请输入1到${pages.value}之间的整数页码`; return }
  await render(value)
}
async function changeZoom(value) { zoom.value = value; await render(page.value) }
async function toggleFullscreen() {
  try { if (window.document.fullscreenElement === viewer.value) await window.document.exitFullscreen(); else await viewer.value.requestFullscreen() }
  catch { error.value = '当前浏览器无法进入全屏，可继续使用内嵌阅读。' }
}
async function fullscreenChanged() { fullscreen.value = window.document.fullscreenElement === viewer.value; await nextTick(); if (document && !busy.value) await render(page.value) }
onMounted(() => window.document.addEventListener('fullscreenchange', fullscreenChanged))
watch(() => props.src, async url => {
  release(); const current = generation; page.value = 1; targetPage.value = 1; zoom.value = 1; pages.value = 0; error.value = ''
  loadingTask = getDocument({ url, isEvalSupported: false, enableXfa: false, useSystemFonts: true })
  try {
    const pdf = await loadingTask.promise
    if (current !== generation) return
    document = pdf; pages.value = pdf.numPages; await render(1, current)
  } catch { if (current === generation) error.value = 'PDF无法解析，请下载审核附件查看。' }
}, { immediate: true })
onBeforeUnmount(() => { window.document.removeEventListener('fullscreenchange', fullscreenChanged); release() })
</script>
<template>
  <div ref="viewer" class="pdf-review"><p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="!pages && !error" class="muted">正在解析PDF…</p>
    <div v-if="pages" class="pdf-toolbar"><span>{{ label }} · 第 {{ page }} / {{ pages }} 页</span><div><button class="secondary" :disabled="busy || page <= 1" @click="render(page - 1)">上一页</button><button class="secondary" :disabled="busy || page >= pages" @click="render(page + 1)">下一页</button></div><form @submit.prevent="jump"><input v-model="targetPage" aria-label="跳转页码" type="number" :min="1" :max="pages" :disabled="busy"/><button class="secondary" :disabled="busy">跳转</button></form><select aria-label="PDF缩放" :value="zoom" :disabled="busy" @change="changeZoom(Number($event.target.value))"><option :value="0.75">75%</option><option :value="1">适应宽度</option><option :value="1.25">125%</option><option :value="1.5">150%</option><option :value="2">200%</option></select><button v-if="fullscreenSupported" class="secondary" @click="toggleFullscreen">{{fullscreen?'退出全屏':'全屏阅读'}}</button></div>
    <div ref="viewportElement" class="pdf-viewport"><canvas ref="canvas" class="pdf-review-canvas" :aria-label="`${label}PDF内容`" /></div>
  </div>
</template>
<style scoped>
.pdf-toolbar { display:flex; align-items:center; gap:10px; flex-wrap:wrap; padding:10px 0; font-size:11px; }
.pdf-toolbar>div,.pdf-toolbar form { display:flex; align-items:center; gap:6px; }
.pdf-toolbar input { width:65px; min-height:34px; padding:5px; }
.pdf-toolbar select { width:110px; min-height:34px; padding:5px; }
.pdf-toolbar .secondary { font-size:11px; padding:5px 9px; min-height:34px; }
.pdf-viewport { overflow:auto; max-height:75vh; padding:12px; }
.pdf-review-canvas { max-width:none; margin:0 auto; }
.pdf-review:fullscreen { background:#edf0f8; padding:20px; color:#202b49; overflow:auto; }
.pdf-review:fullscreen .pdf-viewport { max-height:calc(100vh - 110px); }
</style>

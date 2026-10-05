<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { publishedResourceApi } from '../api/resources.js'
import { timeText } from '../api/resourceState.js'
import { getCourseOptions, getResourceCategoryOptions, getIdeologicalElementOptions } from '../api/baseData.js'
const props = defineProps({ favoritesOnly: { type: Boolean, default: false } })
const filters = reactive({ keyword: '', courseId: '', categoryId: '', elementId: '' })
const courses = ref([]), categories = ref([]), elements = ref([]), items = ref([])
const page = ref(1), total = ref(0), loading = ref(false), error = ref(''), changing = ref(0), size = 10
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let sequence = 0
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try {
    const params = { page: page.value, size }
    if (!props.favoritesOnly) for (const [key, value] of Object.entries(filters)) if (String(value).trim()) params[key] = String(value).trim()
    const result = await (props.favoritesOnly ? publishedResourceApi.favorites(params) : publishedResourceApi.list(params))
    if (current !== sequence) return
    items.value = result.items; total.value = result.total
    if (page.value > pages.value) { page.value = pages.value; await load() }
  } catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}
function search() { page.value = 1; load() }
function changePage(n) { page.value = n; load() }
async function favorite(resource) {
  if (changing.value) return
  changing.value = resource.id; error.value = ''
  try {
    const state = await publishedResourceApi.favorite(resource.id, !resource.favorite)
    resource.favorite = state.favorite
    if (props.favoritesOnly) await load() // List reloads do not create browse events.
  } catch (e) { error.value = e.message }
  finally { changing.value = 0 }
}
watch(() => props.favoritesOnly, search)
onMounted(async () => {
  try { [courses.value, categories.value, elements.value] = await Promise.all([getCourseOptions(), getResourceCategoryOptions(), getIdeologicalElementOptions()]) }
  catch (e) { error.value = e.message }
  await load()
})
</script>
<template>
  <section class="page-heading"><p class="eyebrow">已发布教学资源</p><h1>{{ favoritesOnly ? '我的收藏' : '资源中心' }}</h1><p class="muted">{{ favoritesOnly ? '只展示本人当前收藏且仍已发布的资源。' : '仅展示审核通过并发布的资源，可组合检索课程、分类和课程思政元素。' }}</p></section>
  <section class="card">
    <form v-if="!favoritesOnly" class="filter-row" @submit.prevent="search">
      <div><label for="library-keyword">关键词</label><input id="library-keyword" v-model="filters.keyword" maxlength="200" placeholder="资源标题或简介" /></div>
      <div><label for="library-course">课程</label><select id="library-course" v-model="filters.courseId"><option value="">全部课程</option><option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
      <div><label for="library-category">资源分类</label><select id="library-category" v-model="filters.categoryId"><option value="">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
      <div><label for="library-element">课程思政元素</label><select id="library-element" v-model="filters.elementId"><option value="">全部元素</option><option v-for="e in elements" :key="e.id" :value="e.id">{{ e.name }}</option></select></div>
      <button class="secondary" :disabled="loading">查询</button>
    </form>
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <div class="table-wrap"><table><thead><tr><th>资源 / 附件</th><th>课程 / 分类</th><th>思政元素</th><th>发布教师 / 时间</th><th>操作</th></tr></thead><tbody>
      <tr v-for="r in items" :key="r.id"><td class="base-name">{{ r.title }}<small class="description-cell">{{ r.fileOriginalName }} · {{ r.fileMimeType }} · {{ (r.fileSizeBytes / 1024).toFixed(1) }} KiB</small></td><td>{{ r.courseName }}<br />{{ r.categoryName }}</td><td class="base-name">{{ r.elements.map(e => e.name).join('、') }}</td><td>{{ r.teacherName }}<br />{{ timeText(r.publishedAt) }}</td><td><RouterLink class="link-button" :to="`/resources/${r.id}`">查看详情</RouterLink><button class="link-button" :disabled="!!changing" @click="favorite(r)">{{ r.favorite ? '取消收藏' : '收藏' }}</button></td></tr>
      <tr v-if="loading"><td colspan="5" class="empty">正在加载…</td></tr><tr v-else-if="!items.length"><td colspan="5" class="empty">{{ favoritesOnly ? '暂无有效收藏' : '没有符合条件的已发布资源' }}</td></tr>
    </tbody></table></div>
    <div class="pagination"><span>共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span><div><button class="secondary" :disabled="loading || page <= 1" @click="changePage(page - 1)">上一页</button><button class="secondary" :disabled="loading || page >= pages" @click="changePage(page + 1)">下一页</button></div></div>
  </section>
</template>

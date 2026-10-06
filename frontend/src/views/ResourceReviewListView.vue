<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/client.js'
import { resourceReviewApi } from '../api/resources.js'
import { resourceStates, timeText } from '../api/resourceState.js'
import { getCourseOptions, getResourceCategoryOptions } from '../api/baseData.js'
import { getStatistics } from '../api/statistics.js'
import LoadingState from '../components/LoadingState.vue'
const counts = ref({})
const filters = reactive({ keyword: '', courseId: '', categoryId: '', teacherId: '', status: 'PENDING' })
const courses = ref([]), categories = ref([]), teachers = ref([]), items = ref([])
const page = ref(1), total = ref(0), loading = ref(true), error = ref(''), size = 10
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let sequence = 0
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try {
    const params = { page: page.value, size }
    for (const [key, value] of Object.entries(filters)) if (String(value).trim()) params[key] = String(value).trim()
    const result = await resourceReviewApi.list(params)
    if (current !== sequence) return
    items.value = result.items; total.value = result.total
    const statistics = await getStatistics()
    if (current !== sequence) return
    counts.value = { PENDING: statistics.resources.pending, APPROVED: statistics.resources.approved, REJECTED: statistics.resources.rejected }
    if (page.value > pages.value) { page.value = pages.value; await load() }
  } catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}
function search() { page.value = 1; load() }
function selectStatus(status) { filters.status = status; search() }
function changePage(n) { page.value = n; load() }
onMounted(async () => {
  try {
    [courses.value, categories.value] = await Promise.all([getCourseOptions(), getResourceCategoryOptions()])
    // Page through the existing user API; do not introduce a second user directory.
    let n = 1, result
    do { result = await api(`/users?role=TEACHER&page=${n}&size=100`); teachers.value.push(...result.items); n++ } while ((n - 1) * 100 < result.total)
  } catch (e) { error.value = e.message }
  await load()
})
</script>
<template>
  <section class="page-heading"><p class="eyebrow">教学资源审核</p><h1>资源审核</h1><p class="muted">默认展示待审核资源；可追溯已驳回和已通过的资源，不展示教师草稿。</p></section>
  <section class="card"><div class="status-tabs" aria-label="审核状态"><button v-for="[code,label] in [['PENDING','待审核'],['APPROVED','已通过'],['REJECTED','已驳回'],['ALL','全部']]" :key="code" :disabled="loading" :class="{ active: filters.status === code }" @click="selectStatus(code)">{{ label }} <b>{{ loading ? '—' : code === 'ALL' ? Object.values(counts).reduce((n, v) => n + v, 0) : counts[code] || 0 }}</b></button></div><form class="filter-row" @submit.prevent="search">
    <div><label for="review-keyword">资源标题</label><input id="review-keyword" v-model="filters.keyword" maxlength="200" /></div>
    <div><label for="review-course">课程</label><select id="review-course" v-model="filters.courseId"><option value="">全部课程</option><option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
    <div><label for="review-category">分类</label><select id="review-category" v-model="filters.categoryId"><option value="">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
    <div><label for="review-teacher">教师</label><select id="review-teacher" v-model="filters.teacherId"><option value="">全部教师</option><option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.displayName }}（{{ t.username }}）</option></select></div>
    <div><label for="review-status">审核状态</label><select id="review-status" v-model="filters.status"><option value="PENDING">待审核</option><option value="REJECTED">审核驳回</option><option value="APPROVED">审核通过</option><option value="ALL">全部已提交状态</option></select></div>
    <button class="secondary" :disabled="loading">查询</button>
  </form><p v-if="error" class="message error" role="alert">{{ error }}</p>
  <LoadingState v-if="loading" />
  <div class="table-wrap"><table><thead><tr><th>资源 / 附件</th><th>教师</th><th>课程 / 分类</th><th>思政元素</th><th>轮次 / 本轮提交时间</th><th>状态</th><th>操作</th></tr></thead><tbody>
    <tr v-for="r in items" :key="r.id"><td class="base-name">{{ r.title }}<small class="description-cell">{{ r.fileOriginalName }} · {{ (r.fileSizeBytes / 1024).toFixed(1) }} KiB</small></td><td>{{ r.teacherName }}</td><td>{{ r.courseName }}<br />{{ r.categoryName }}</td><td class="base-name">{{ r.elements.map(e => e.name).join('、') }}</td><td>第 {{ r.submissionNo }} 轮<br />{{ timeText(r.pendingSubmittedAt) }}</td><td><span class="badge" :class="`state-${r.status}`">{{ resourceStates[r.status] }}</span></td><td><RouterLink class="link-button" :to="`/resource-reviews/${r.id}`">{{ r.status === 'PENDING' ? '查看并审核' : '详情与历史' }}</RouterLink></td></tr>
    <tr v-if="loading"><td colspan="7" class="empty">正在加载…</td></tr><tr v-else-if="!items.length"><td colspan="7" class="empty">暂无符合条件的资源</td></tr>
  </tbody></table></div><div class="pagination"><span>共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span><div><button class="secondary" :disabled="loading || page <= 1" @click="changePage(page - 1)">上一页</button><button class="secondary" :disabled="loading || page >= pages" @click="changePage(page + 1)">下一页</button></div></div>
  </section>
</template>

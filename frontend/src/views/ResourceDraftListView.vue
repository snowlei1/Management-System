<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { resourceDraftApi } from '../api/resources.js'
import { getCourseOptions, getResourceCategoryOptions } from '../api/baseData.js'
import { resourceStates, editableResource } from '../api/resourceState.js'
import ResourceSubmitDialog from '../components/ResourceSubmitDialog.vue'
import { useRoute } from 'vue-router'
import { portalApi } from '../api/portal.js'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const filters = reactive({ keyword: '', courseId: '', categoryId: '', status: resourceStates[route.query.status] ? route.query.status : '' })
const counts = ref({}), auditById = ref({})
const allCount = computed(() => Object.values(counts.value).reduce((sum, n) => sum + n, 0))
const courses = ref([]), categories = ref([]), items = ref([])
const page = ref(1), total = ref(0), loading = ref(true), deleting = ref(false)
const error = ref(''), notice = ref(''), target = ref(null)
const submitTarget = ref(null)
const size = 10
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let sequence = 0
async function load() {
  const current = ++sequence
  loading.value = true; error.value = ''
  try {
    const params = { page: page.value, size }
    for (const [key, value] of Object.entries(filters)) if (String(value).trim()) params[key] = String(value).trim()
    const result = await resourceDraftApi.list(params)
    if (current !== sequence) return
    items.value = result.items; total.value = result.total
    const [dashboard, presentations] = await Promise.all([portalApi.dashboard(), portalApi.ownPresentations(result.items.map(r => r.id))])
    if (current !== sequence) return
    counts.value = dashboard.counts
    auditById.value = Object.fromEntries(presentations.map(row => [row.resource.id, row.latestAudit]))
    if (page.value > pages.value) { page.value = pages.value; await load() }
  } catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) loading.value = false }
}
function search() { page.value = 1; load() }
function selectStatus(status) { filters.status = status; search() }
function submitted() { submitTarget.value = null; notice.value = '资源已提交，待审核期间不可编辑。'; load() }
function changePage(next) { if (next >= 1 && next <= pages.value) { page.value = next; load() } }
async function remove() {
  if (deleting.value || !target.value) return
  deleting.value = true; error.value = ''
  try {
    await resourceDraftApi.remove(target.value.id)
    target.value = null; notice.value = '草稿已软删除，记录及关联保留。'
    await load()
  } catch (e) { error.value = e.message }
  finally { deleting.value = false }
}
onMounted(async () => {
  try { [courses.value, categories.value] = await Promise.all([getCourseOptions(), getResourceCategoryOptions()]) }
  catch (e) { error.value = e.message }
  await load()
})
</script>

<template>
  <section class="page-heading heading-row">
    <div><p class="eyebrow">教学资源管理</p><h1>我的资源</h1><p class="muted">本人资源的建设与审核进度；待审核及已发布内容不能直接修改。</p></div>
    <RouterLink class="primary" to="/my-resources/new">新建资源</RouterLink>
  </section>
  <section class="card">
    <div class="status-tabs" aria-label="本人资源状态"><button :disabled="loading" :class="{ active: !filters.status }" @click="selectStatus('')">全部 <b>{{ loading ? '—' : allCount }}</b></button><button v-for="(name, code) in resourceStates" :key="code" :disabled="loading" :class="{ active: filters.status === code }" @click="selectStatus(code)">{{ name }} <b>{{ loading ? '—' : counts[code] || 0 }}</b></button></div>
    <form class="filter-row" @submit.prevent="search">
      <div><label for="resource-keyword">资源标题</label><input id="resource-keyword" v-model="filters.keyword" maxlength="200" placeholder="输入关键词" /></div>
      <div><label for="resource-course-filter">课程</label><select id="resource-course-filter" v-model="filters.courseId"><option value="">全部课程</option><option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
      <div><label for="resource-category-filter">分类</label><select id="resource-category-filter" v-model="filters.categoryId"><option value="">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></div>
      <div><label for="resource-status-filter">状态</label><select id="resource-status-filter" v-model="filters.status"><option value="">全部状态</option><option v-for="(name, code) in resourceStates" :key="code" :value="code">{{ name }}</option></select></div>
      <button class="secondary" type="submit" :disabled="loading">查询</button>
    </form>
    <p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="notice" class="message success" role="status">{{ notice }}</p>
    <LoadingState v-if="loading" />
    <div class="table-wrap"><table><thead><tr><th>资源名称</th><th>课程</th><th>分类</th><th>思政元素</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
      <tr v-for="r in items" :key="r.id"><td class="base-name">{{ r.title }}<p v-if="r.status === 'REJECTED' && auditById[r.id]?.reason" class="rejection-item clamp-3">驳回原因：{{ auditById[r.id].reason }}</p></td><td>{{ r.courseName }}</td><td>{{ r.categoryName }}</td><td class="base-name">{{ r.elements.map(e => e.name).join('、') || '尚未标注' }}</td><td><span class="badge" :class="`state-${r.status}`">{{ resourceStates[r.status] }}</span></td><td>{{ r.updatedAt?.replace('T', ' ').slice(0, 19) }}<br />第 {{ r.submissionNo }} 轮</td><td class="actions"><RouterLink class="link-button" :to="`/my-resources/${r.id}`">详情</RouterLink><RouterLink v-if="editableResource(r)" class="link-button" :to="`/my-resources/${r.id}/edit`">编辑</RouterLink><button v-if="r.status === 'DRAFT'" class="link-button" type="button" @click="target = r; error = ''">删除</button><button v-if="editableResource(r)" class="link-button" @click="submitTarget = r">{{ r.status === 'REJECTED' ? '重新提交' : '提交审核' }}</button></td></tr>
      <tr v-if="!loading && !items.length"><td colspan="7"><EmptyState title="暂无符合条件的资源" description="创建草稿开始建设，或调整筛选条件。" /></td></tr>
    </tbody></table></div>
    <div class="pagination"><span>共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span><div><button class="secondary" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button><button class="secondary" :disabled="page >= pages || loading" @click="changePage(page + 1)">下一页</button></div></div>
  </section>
  <ResourceSubmitDialog v-if="submitTarget" :resource="submitTarget" @close="submitTarget = null" @submitted="submitted" />
  <div v-if="target" class="modal-backdrop" @click.self="!deleting && (target = null)"><section class="modal-card confirm-card" role="dialog" aria-modal="true" aria-label="确认删除草稿"><h2>确认删除草稿</h2><p>“{{ target.title }}”将从本人列表移除。数据库记录、思政元素关联与当前文件保留，不执行物理删除。</p><p v-if="error" class="message error" role="alert">{{ error }}</p><div class="modal-actions"><button class="secondary" :disabled="deleting" @click="target = null">取消</button><button class="primary" :disabled="deleting" @click="remove">{{ deleting ? '提交中…' : '确认删除' }}</button></div></section></div>
</template>

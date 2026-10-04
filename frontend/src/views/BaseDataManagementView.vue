<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { baseDataApi } from '../api/baseData.js'

const props = defineProps({ kind: { type: String, required: true } })
const definitions = {
  courses: { label: '课程', nameLimit: 120, descriptionLabel: '课程简介', hasCode: true },
  'ideological-elements': { label: '课程思政元素', nameLimit: 100, descriptionLabel: '元素说明', hasCode: false },
  'resource-categories': { label: '资源分类', nameLimit: 80, descriptionLabel: '分类说明', hasCode: false },
}
const definition = computed(() => definitions[props.kind])
const router = useRouter()
const filters = reactive({ keyword: '', status: '' })
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const loading = ref(false)
const saving = ref(false)
const changingStatus = ref(false)
const error = ref('')
const notice = ref('')
const formOpen = ref(false)
const editingId = ref(null)
const statusTarget = ref(null)
const form = reactive({ courseCode: '', name: '', description: '' })
let loadSequence = 0

function handleError(exception) {
  error.value = exception.message || '操作失败'
  if (exception.status === 401) router.replace('/login')
}

async function load() {
  const sequence = ++loadSequence
  const kind = props.kind
  loading.value = true
  error.value = ''
  try {
    const params = { page: String(page.value), size: String(size) }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.status) params.status = filters.status
    const result = await baseDataApi.list(kind, params)
    if (sequence !== loadSequence) return
    list.value = result.items
    total.value = result.total
  } catch (exception) {
    if (sequence === loadSequence) handleError(exception)
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function search() { page.value = 1; load() }
function changePage(next) {
  if (next < 1 || next > totalPages.value || loading.value) return
  page.value = next
  load()
}
function resetForm() {
  form.courseCode = ''
  form.name = ''
  form.description = ''
  editingId.value = null
  formOpen.value = false
}
function closeForm() { if (!saving.value) resetForm() }
function openCreate() {
  resetForm()
  error.value = ''
  notice.value = ''
  formOpen.value = true
}
function openEdit(item) {
  resetForm()
  editingId.value = item.id
  form.courseCode = item.courseCode || ''
  form.name = item.name
  form.description = item.description || ''
  error.value = ''
  notice.value = ''
  formOpen.value = true
}
async function save() {
  if (saving.value) return
  const kind = props.kind
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = { name: form.name.trim(), description: form.description.trim() || null }
    if (definition.value.hasCode) payload.courseCode = form.courseCode.trim()
    if (!payload.name || (definition.value.hasCode && !payload.courseCode)) throw new Error('请填写所有必填字段')
    if (editingId.value) await baseDataApi.update(kind, editingId.value, payload)
    else await baseDataApi.create(kind, payload)
    if (kind !== props.kind) return
    notice.value = editingId.value ? `${definition.value.label}已更新` : `${definition.value.label}已创建`
    resetForm()
    await load()
  } catch (exception) {
    if (kind === props.kind) handleError(exception)
  } finally { saving.value = false }
}
function askStatusChange(item) { error.value = ''; statusTarget.value = item }
function closeStatus() { if (!changingStatus.value) statusTarget.value = null }
async function confirmStatusChange() {
  const item = statusTarget.value
  if (!item || changingStatus.value) return
  const kind = props.kind
  const next = item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  changingStatus.value = true
  error.value = ''
  notice.value = ''
  try {
    await baseDataApi.setStatus(kind, item.id, next)
    if (kind !== props.kind) return
    notice.value = `${definition.value.label}已${next === 'ACTIVE' ? '启用' : '停用'}`
    statusTarget.value = null
    await load()
  } catch (exception) {
    if (kind === props.kind) handleError(exception)
  } finally { changingStatus.value = false }
}

watch(() => props.kind, () => {
  filters.keyword = ''
  filters.status = ''
  page.value = 1
  list.value = []
  total.value = 0
  error.value = ''
  notice.value = ''
  resetForm()
  statusTarget.value = null
  load()
}, { immediate: true })
</script>

<template>
  <section class="page-heading heading-row">
    <div><p class="eyebrow">基础数据管理</p><h1>{{ definition.label }}管理</h1><p class="muted">由管理员统一维护；停用后不可供新资源选择，已有历史关联保留。</p></div>
    <button class="primary" type="button" @click="openCreate">新增{{ definition.label }}</button>
  </section>
  <section class="card">
    <form class="filter-row" @submit.prevent="search">
      <div><label for="base-keyword">{{ definition.hasCode ? '课程编号或名称' : '名称' }}</label><input id="base-keyword" v-model="filters.keyword" maxlength="120" placeholder="输入关键词" /></div>
      <div><label for="base-status-filter">状态</label><select id="base-status-filter" v-model="filters.status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></div>
      <button type="submit" class="secondary" :disabled="loading">查询</button>
    </form>
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="message success" role="status">{{ notice }}</p>
    <div class="table-wrap">
      <table>
        <thead><tr><th>ID</th><th v-if="definition.hasCode">课程编号</th><th>名称</th><th>{{ definition.descriptionLabel }}</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in list" :key="item.id">
            <td>{{ item.id }}</td><td v-if="definition.hasCode">{{ item.courseCode }}</td><td class="base-name">{{ item.name }}</td>
            <td><span class="description-cell" :title="item.description || ''">{{ item.description || '—' }}</span></td>
            <td><span class="badge" :class="item.status === 'ACTIVE' ? 'badge-active' : 'badge-disabled'">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</span></td>
            <td>{{ item.updatedAt?.replace('T', ' ').slice(0, 19) }}</td>
            <td class="actions"><button type="button" class="link-button" @click="openEdit(item)">编辑</button><button type="button" class="link-button" @click="askStatusChange(item)">{{ item.status === 'ACTIVE' ? '停用' : '启用' }}</button></td>
          </tr>
          <tr v-if="loading"><td :colspan="definition.hasCode ? 7 : 6" class="empty">正在加载…</td></tr>
          <tr v-else-if="!list.length"><td :colspan="definition.hasCode ? 7 : 6" class="empty">没有符合条件的数据</td></tr>
        </tbody>
      </table>
    </div>
    <div class="pagination"><span>共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span><div><button type="button" class="secondary" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button><button type="button" class="secondary" :disabled="page >= totalPages || loading" @click="changePage(page + 1)">下一页</button></div></div>
  </section>
  <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
    <section class="modal-card" role="dialog" aria-modal="true" :aria-label="(editingId ? '编辑' : '新增') + definition.label">
      <div class="modal-heading"><h2>{{ editingId ? '编辑' : '新增' }}{{ definition.label }}</h2><button type="button" class="text-button" :disabled="saving" @click="closeForm">关闭</button></div>
      <form @submit.prevent="save">
        <template v-if="definition.hasCode"><label for="base-code">课程编号</label><input id="base-code" v-model="form.courseCode" maxlength="40" required /></template>
        <label for="base-name">{{ definition.label }}名称</label><input id="base-name" v-model="form.name" :maxlength="definition.nameLimit" required />
        <label for="base-description">{{ definition.descriptionLabel }}（可选）</label><textarea id="base-description" v-model="form.description" maxlength="1000" rows="5" />
        <p class="field-hint">名称与编号前后空白会被移除；名称长度上限为 {{ definition.nameLimit }} 个字符。新增数据默认启用。</p>
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <div class="modal-actions"><button type="button" class="secondary" :disabled="saving" @click="closeForm">取消</button><button type="submit" class="primary" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button></div>
      </form>
    </section>
  </div>
  <div v-if="statusTarget" class="modal-backdrop" @click.self="closeStatus">
    <section class="modal-card confirm-card" role="dialog" aria-modal="true" aria-label="确认基础数据状态">
      <h2>确认{{ statusTarget.status === 'ACTIVE' ? '停用' : '启用' }}{{ definition.label }}</h2>
      <p>“{{ statusTarget.name }}”将被{{ statusTarget.status === 'ACTIVE' ? '停用，停止供新资源选择；已有历史关联不会被删除' : '启用，恢复为可选基础数据' }}。</p>
      <p v-if="error" class="message error" role="alert">{{ error }}</p>
      <div class="modal-actions"><button type="button" class="secondary" :disabled="changingStatus" @click="closeStatus">取消</button><button type="button" class="primary" :disabled="changingStatus" @click="confirmStatusChange">{{ changingStatus ? '提交中…' : '确认' }}</button></div>
    </section>
  </div>
</template>

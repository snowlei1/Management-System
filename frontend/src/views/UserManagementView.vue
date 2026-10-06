<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/client.js'
import { authState } from '../auth/state.js'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const filters = reactive({ keyword: '', role: '', status: '' })
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const formOpen = ref(false)
const editingId = ref(null)
const editingAdmin = ref(false)
const statusTarget = ref(null)
const form = reactive({ username: '', displayName: '', role: 'TEACHER', password: '' })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const roleNames = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
const statusNames = { ACTIVE: '启用', DISABLED: '停用' }

function handleError(exception) {
  error.value = exception.message || '操作失败'
  if (exception.status === 401) router.replace('/login')
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), size: String(size) })
    if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim())
    if (filters.role) params.set('role', filters.role)
    if (filters.status) params.set('status', filters.status)
    const result = await api(`/users?${params}`)
    list.value = result.items
    total.value = result.total
  } catch (exception) {
    handleError(exception)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(next) {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  load()
}

function resetForm() {
  form.username = ''
  form.displayName = ''
  form.role = 'TEACHER'
  form.password = ''
  editingId.value = null
  editingAdmin.value = false
  formOpen.value = false
}

function openCreate() {
  resetForm()
  error.value = ''
  notice.value = ''
  formOpen.value = true
}

function openEdit(user) {
  resetForm()
  editingId.value = user.id
  editingAdmin.value = user.role === 'ADMIN'
  form.username = user.username
  form.displayName = user.displayName
  form.role = user.role
  error.value = ''
  notice.value = ''
  formOpen.value = true
}

async function save() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = {
      username: form.username.trim(), displayName: form.displayName.trim(), role: form.role,
    }
    let saved
    if (editingId.value) {
      saved = await api(`/users/${editingId.value}`, { method: 'PUT', body: payload })
    } else {
      saved = await api('/users', { method: 'POST', body: { ...payload, password: form.password } })
    }
    if (saved.id === authState.user?.id) authState.user = saved
    notice.value = editingId.value ? '用户信息已更新' : '用户已创建'
    resetForm()
    await load()
  } catch (exception) {
    handleError(exception)
  } finally {
    saving.value = false
  }
}

function askStatusChange(user) {
  statusTarget.value = user
}

async function confirmStatusChange() {
  const user = statusTarget.value
  if (!user) return
  const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  error.value = ''
  notice.value = ''
  try {
    await api(`/users/${user.id}/status`, { method: 'PATCH', body: { status: next } })
    notice.value = `账号已${next === 'DISABLED' ? '禁用' : '启用'}`
    statusTarget.value = null
    await load()
  } catch (exception) {
    handleError(exception)
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading heading-row">
    <div><p class="eyebrow">管理员工作台</p><h1>用户管理</h1><p class="muted">维护教师和学生账号；管理员账号不允许停用。</p></div>
    <button class="primary" type="button" @click="openCreate">新增用户</button>
  </section>

  <section class="card">
    <form class="filter-row" @submit.prevent="search">
      <div><label for="keyword">账号或姓名</label><input id="keyword" v-model="filters.keyword" maxlength="80" placeholder="输入关键词" /></div>
      <div><label for="role-filter">角色</label><select id="role-filter" v-model="filters.role"><option value="">全部角色</option><option value="ADMIN">管理员</option><option value="TEACHER">教师</option><option value="STUDENT">学生</option></select></div>
      <div><label for="status-filter">状态</label><select id="status-filter" v-model="filters.status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></div>
      <button class="secondary" type="submit">查询</button>
    </form>
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <p v-if="notice" class="message success" role="status">{{ notice }}</p>
    <LoadingState v-if="loading" />
    <p class="field-hint">共 {{ total }} 个账号，教师与学生由管理员维护；账号停用后已有会话失效。</p>
    <div class="table-wrap">
      <table>
        <thead><tr><th>ID</th><th>账号</th><th>姓名</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="user in list" :key="user.id">
            <td>{{ user.id }}</td><td>{{ user.username }}</td><td>{{ user.displayName }}</td>
            <td>{{ roleNames[user.role] }}</td>
            <td><span class="badge" :class="user.status === 'ACTIVE' ? 'badge-active' : 'badge-disabled'">{{ statusNames[user.status] }}</span></td>
            <td>{{ user.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
            <td class="actions"><button class="link-button" type="button" @click="openEdit(user)">编辑</button>
              <button v-if="user.role !== 'ADMIN'" class="link-button" type="button" @click="askStatusChange(user)">{{ user.status === 'ACTIVE' ? '禁用' : '启用' }}</button></td>
          </tr>
          <tr v-if="!loading && !list.length"><td colspan="7"><EmptyState title="没有符合条件的用户" description="调整账号、角色或状态筛选后重试。" icon="users" /></td></tr>
          <tr v-if="loading"><td colspan="7" class="empty">正在加载…</td></tr>
        </tbody>
      </table>
    </div>
    <div class="pagination"><span>共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span><div><button type="button" class="secondary" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button><button type="button" class="secondary" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button></div></div>
  </section>

  <div v-if="formOpen" class="modal-backdrop" @click.self="resetForm">
    <section class="modal-card" role="dialog" aria-modal="true" :aria-label="editingId ? '编辑用户' : '新增用户'">
      <div class="modal-heading"><h2>{{ editingId ? '编辑用户' : '新增用户' }}</h2><button type="button" class="text-button" @click="resetForm">关闭</button></div>
      <form @submit.prevent="save">
        <label for="form-username">账号</label><input id="form-username" v-model="form.username" maxlength="64" :disabled="editingAdmin" required />
        <label for="form-name">姓名</label><input id="form-name" v-model="form.displayName" maxlength="80" required />
        <label for="form-role">角色</label><select id="form-role" v-model="form.role" :disabled="editingAdmin"><option v-if="editingAdmin" value="ADMIN">管理员</option><option value="TEACHER">教师</option><option value="STUDENT">学生</option></select>
        <template v-if="!editingId"><label for="form-password">初始密码</label><input id="form-password" v-model="form.password" type="password" minlength="8" autocomplete="new-password" required /><p class="field-hint">至少 8 字节；请通过安全渠道告知新用户。</p></template>
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <div class="modal-actions"><button type="button" class="secondary" @click="resetForm">取消</button><button type="submit" class="primary" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button></div>
      </form>
    </section>
  </div>

  <div v-if="statusTarget" class="modal-backdrop" @click.self="statusTarget = null">
    <section class="modal-card confirm-card" role="dialog" aria-modal="true" aria-label="确认用户状态">
      <h2>确认{{ statusTarget.status === 'ACTIVE' ? '禁用' : '启用' }}账号</h2>
      <p>账号 <strong>{{ statusTarget.username }}</strong> 将被{{ statusTarget.status === 'ACTIVE' ? '禁用，且现有登录会话会失效' : '启用' }}。</p>
      <p v-if="error" class="message error" role="alert">{{ error }}</p>
      <div class="modal-actions"><button type="button" class="secondary" @click="statusTarget = null">取消</button><button type="button" class="primary" @click="confirmStatusChange">确认</button></div>
    </section>
  </div>
</template>

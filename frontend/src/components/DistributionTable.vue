<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ title: String, rows: { type: Array, default: () => [] } })
const page = ref(1)
const pages = computed(() => Math.max(1, Math.ceil(props.rows.length / 10)))
const items = computed(() => props.rows.slice((page.value - 1) * 10, page.value * 10))
watch(() => props.rows, () => { page.value = 1 })
</script>

<template>
  <section class="card statistics-section">
    <h2>{{ title }}</h2>
    <div class="table-wrap"><table>
      <thead><tr><th>名称</th><th>基础数据状态</th><th>已发布资源数</th></tr></thead>
      <tbody>
        <tr v-for="row in items" :key="row.id"><td class="base-name">{{ row.name }}</td>
          <td>{{ row.status === 'ACTIVE' ? '启用' : '停用（保留历史关联）' }}</td><td>{{ row.resourceCount }}</td></tr>
        <tr v-if="!items.length"><td colspan="3" class="empty">暂无基础数据</td></tr>
      </tbody>
    </table></div>
    <div class="pagination"><span>共 {{ rows.length }} 项 · 第 {{ page }} / {{ pages }} 页</span><div>
      <button class="secondary" :disabled="page <= 1" @click="page--">上一页</button>
      <button class="secondary" :disabled="page >= pages" @click="page++">下一页</button>
    </div></div>
  </section>
</template>

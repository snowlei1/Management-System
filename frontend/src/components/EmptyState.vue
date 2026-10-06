<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ title: { type: String, default: '暂无数据' }, description: { type: String, default: '可调整筛选条件后重试。' }, icon: { type: String, default: 'file' } })
</script>
<template><div class="empty-state"><AppIcon :name="icon" :size="30"/><h3>{{ title }}</h3><p>{{ description }}</p><slot/></div></template>

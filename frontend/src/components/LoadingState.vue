<script setup>defineProps({ label: { type: String, default: '正在加载数据…' } })</script>
<template><div class="loading-state" role="status" :aria-label="label"><span>{{ label }}</span><div class="skeleton-line"/><div class="skeleton-line short"/></div></template>

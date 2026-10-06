<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import AppIcon from './AppIcon.vue'
const messages = ref([]), timers = new Map(); let sequence = 0
function remove(id) { messages.value = messages.value.filter(m => m.id !== id); clearTimeout(timers.get(id)); timers.delete(id) }
function receive(event) { const id = ++sequence; messages.value.push({ id, ...event.detail }); timers.set(id, setTimeout(() => remove(id), 5000)) }
onMounted(() => window.addEventListener('app-toast', receive))
onBeforeUnmount(() => { window.removeEventListener('app-toast', receive); timers.forEach(clearTimeout) })
</script>
<template><div class="toast-stack" aria-live="polite"><div v-for="m in messages" :key="m.id" class="toast" :class="m.type" role="status"><AppIcon :name="m.type === 'error' ? 'rejected' : 'approved'"/><span>{{ m.message }}</span><button aria-label="关闭提示" class="text-button" @click="remove(m.id)"><AppIcon name="close" :size="16"/></button></div></div></template>

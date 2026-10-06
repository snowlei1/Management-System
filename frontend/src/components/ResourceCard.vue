<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { timeText } from '../api/resourceState.js'
const props = defineProps({ resource: { type: Object, required: true }, changing: Boolean, horizontal: Boolean, showFavorite: { type: Boolean, default: true } })
defineEmits(['favorite'])
const type = computed(() => props.resource.fileOriginalName?.split('.').pop().toUpperCase() || '文件')
const icon = computed(() => ['PNG', 'JPG', 'JPEG'].includes(type.value) ? 'image' : ['XLS', 'XLSX'].includes(type.value) ? 'spreadsheet' : ['PPT', 'PPTX'].includes(type.value) ? 'presentation' : 'file')
</script>
<template><article class="resource-card" :class="{ horizontal }"><div class="resource-card-top"><span class="file-icon" :class="type.toLowerCase()"><AppIcon :name="icon" :size="27"/></span><span class="file-label">{{ type }} · 已发布</span><button v-if="showFavorite" class="favorite-icon" :class="{ selected: resource.favorite }" :aria-label="resource.favorite ? '取消收藏' : '收藏'" :disabled="changing" @click="$emit('favorite', resource)"><AppIcon name="favorite" :size="18"/></button></div>
  <RouterLink :to="`/resources/${resource.id}`" class="resource-card-title">{{ resource.title }}</RouterLink><p class="clamp-2 resource-summary">{{ resource.description || '暂无资源简介，可进入详情查看附件信息。' }}</p>
  <div class="resource-meta"><AppIcon name="course" :size="15"/><span>{{ resource.courseName }}</span><span class="category-tag">{{ resource.categoryName }}</span></div><div class="tag-list"><span v-for="e in resource.elements" :key="e.id" class="tag">{{ e.name }}</span></div>
  <footer><span>{{ resource.teacherName }}</span><time>{{ timeText(resource.publishedAt).slice(0, 10) }}</time><RouterLink :to="`/resources/${resource.id}`" aria-label="查看资源详情"><AppIcon name="arrow" :size="17"/></RouterLink></footer></article></template>

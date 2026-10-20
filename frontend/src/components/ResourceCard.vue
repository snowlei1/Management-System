<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import HighlightText from './HighlightText.vue'
import { timeText } from '../api/resourceState.js'
const props = defineProps({ resource: { type: Object, required: true }, changing: Boolean, horizontal: Boolean, visualCover: Boolean, keyword: String, folders:Array, showFavorite: { type: Boolean, default: true } })
defineEmits(['favorite','move'])
const coverFailed=ref(false)
watch(()=>props.resource.id,()=>coverFailed.value=false)
const type = computed(() => props.resource.fileOriginalName?.split('.').pop().toUpperCase() || '文件')
const icon = computed(() => ['PNG', 'JPG', 'JPEG'].includes(type.value) ? 'image' : ['XLS', 'XLSX'].includes(type.value) ? 'spreadsheet' : ['PPT', 'PPTX'].includes(type.value) ? 'presentation' : 'file')
</script>
<template><article class="resource-card" :class="{ horizontal, 'has-visual-cover': visualCover }">
  <!-- A file-type design cover, not a thumbnail or an attachment preview. -->
  <img v-if="resource.hasCover&&!coverFailed" class="resource-cover-image" :src="`/api/resources/${resource.id}/cover`" :alt="`${resource.title}封面`" loading="lazy" @error="coverFailed=true"/>
  <div v-else-if="visualCover" class="resource-visual-cover" :class="`cover-${icon}`" aria-hidden="true"><span class="cover-type">{{ type }} / 教学资源</span><strong>{{ resource.categoryName }}</strong><AppIcon :name="icon" :size="72"/><small>{{ resource.courseName }}</small></div>
  <div class="resource-card-top"><span class="file-icon" :class="type.toLowerCase()"><AppIcon :name="icon" :size="27"/></span><span class="file-label">{{ type }} · 已发布</span><button v-if="showFavorite" class="favorite-icon" :class="{ selected: resource.favorite }" :aria-label="resource.favorite ? '取消收藏' : '收藏'" :aria-pressed="!!resource.favorite" :disabled="changing" @click="$emit('favorite', resource)"><AppIcon name="favorite" :size="18"/></button></div>
  <RouterLink :to="`/resources/${resource.id}`" class="resource-card-title"><HighlightText :text="resource.title" :keyword="keyword"/></RouterLink><p class="clamp-2 resource-summary"><HighlightText :text="resource.description || '暂无资源简介，可进入详情查看附件信息。'" :keyword="keyword"/></p>
  <div class="resource-meta"><AppIcon name="course" :size="15"/><span>{{ resource.courseName }}</span><span class="category-tag">{{ resource.categoryName }}</span></div><div class="tag-list"><span v-for="e in resource.elements" :key="e.id" class="tag">{{ e.name }}</span></div>
  <footer><span>{{ resource.teacherName }}</span><time>{{ timeText(resource.publishedAt).slice(0, 10) }}</time><RouterLink :to="`/resources/${resource.id}`" aria-label="查看资源详情"><AppIcon name="arrow" :size="17"/></RouterLink></footer><select v-if="folders" :aria-label="`${resource.title}的收藏夹`" :value="resource.favoriteFolderId||''" :disabled="changing" @change="$emit('move',resource,$event.target.value)"><option value="">未分组</option><option v-for="f in folders" :key="f.id" :value="f.id">{{f.name}}</option></select></article></template>
<style scoped>.resource-cover-image{width:100%;height:170px;object-fit:cover;border-radius:7px;margin-bottom:12px}.resource-card>select{margin-top:12px;font-size:12px;min-height:34px}</style>

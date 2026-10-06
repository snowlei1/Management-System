<script setup>
import { resourceStates, timeText } from '../api/resourceState.js'
defineProps({ resource: { type: Object, required: true } })
</script>
<template>
  <h2>{{ resource.title }}</h2><span class="badge" :class="`state-${resource.status}`">{{ resourceStates[resource.status] }} · {{ resource.status }}</span>
  <dl class="resource-details">
    <dt>资源简介</dt><dd class="preserve-lines">{{ resource.description || '—' }}</dd>
    <dt>创建教师</dt><dd>{{ resource.teacherName }}</dd>
    <dt>所属课程</dt><dd>{{ resource.courseName }}{{ resource.courseStatus === 'INACTIVE' ? '（已停用）' : '' }}</dd>
    <dt>资源分类</dt><dd>{{ resource.categoryName }}{{ resource.categoryStatus === 'INACTIVE' ? '（已停用）' : '' }}</dd>
    <dt>思政元素</dt><dd>{{ resource.elements.map(e => e.name + (e.status === 'INACTIVE' ? '（已停用）' : '')).join('、') || '尚未标注（草稿允许，提交至少一个有效元素）' }}</dd>
    <dt>附件名称</dt><dd>{{ resource.fileOriginalName }}</dd><dt>附件类型</dt><dd>{{ resource.fileMimeType }}</dd>
    <dt>附件大小</dt><dd>{{ resource.fileSizeBytes }} 字节（{{ (resource.fileSizeBytes / 1024).toFixed(1) }} KiB）</dd>
    <dt>提交轮次</dt><dd>{{ resource.submissionNo }}</dd>
    <template v-if="resource.pendingSubmittedAt"><dt>本轮提交时间</dt><dd>{{ timeText(resource.pendingSubmittedAt) }}</dd></template>
    <dt>发布时间</dt><dd>{{ timeText(resource.publishedAt) }}</dd>
    <dt>创建时间</dt><dd>{{ timeText(resource.createdAt) }}</dd><dt>更新时间</dt><dd>{{ timeText(resource.updatedAt) }}</dd>
  </dl>
</template>

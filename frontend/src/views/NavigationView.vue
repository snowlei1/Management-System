<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { portalApi } from '../api/portal.js'
import AppIcon from '../components/AppIcon.vue'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
import ResourceLibraryView from './ResourceLibraryView.vue'
const props=defineProps({topics:Boolean}),route=useRoute(),items=ref([]),detail=ref(null),loading=ref(false),error=ref(''),keyword=ref('')
const base=computed(()=>props.topics?'/ideological-topics':'/course-resources'),filtered=computed(()=>items.value.filter(x=>(x.name+(x.code||'')+(x.description||'')).toLowerCase().includes(keyword.value.trim().toLowerCase())))
// Per-component, short-lived presentation cache; never persist another Session's data.
const courseRelations = ref({}), relationCache = new Map()
let sequence=0
async function loadCourseRelations(rows, current) {
  const queue = [...rows]
  async function worker() {
    while (queue.length && current === sequence) {
      const row = queue.shift(), cached = relationCache.get(row.id)
      if (cached && Date.now() - cached.at < 60000) { courseRelations.value[row.id] = { items:cached.items }; continue }
      courseRelations.value[row.id] = { loading:true }
      try {
        const value = await portalApi.navigation(false, row.id)
        if (current !== sequence) return
        relationCache.set(row.id, { items:value.related, at:Date.now() })
        courseRelations.value[row.id] = { items:value.related }
      } catch {
        if (current !== sequence) return
        courseRelations.value[row.id] = { error:true }
      }
    }
  }
  await Promise.all(Array.from({ length:Math.min(3, rows.length) }, worker))
}
watch(()=>[props.topics,route.params.id],async()=>{const current=++sequence;loading.value=true;error.value='';detail.value=null;try{if(route.params.id){const value=await portalApi.navigation(props.topics,route.params.id);if(current===sequence)detail.value=value}else{const value=await(props.topics?portalApi.topics():portalApi.courses());if(current===sequence){items.value=value;if(!props.topics)void loadCourseRelations(value,current)}}}catch(e){if(current===sequence)error.value=e.message}finally{if(current===sequence)loading.value=false}},{immediate:true})
onBeforeUnmount(()=>{sequence++;relationCache.clear()})
</script>
<template>
  <section class="page-heading heading-row" :class="{ 'topic-banner':topics, 'topic-feature-head':topics&&detail }">
    <div><p class="eyebrow">{{topics?'课程思政 / 专题栏目':'教学资源 / 课程导航'}}</p><h1>{{detail ? detail.item.name : topics ? '课程思政专题资源' : '课程资源导航'}}</h1><p class="muted">{{detail?.item.description || (topics ? '围绕课程思政元素组织教学材料，跨课程查阅相关资源。' : '按课程查阅已发布教学材料及其关联的思政元素。')}}</p></div>
    <RouterLink v-if="route.params.id" class="secondary" :to="base">返回{{topics?'专题':'课程'}}列表</RouterLink><span v-else class="count-pill">{{loading ? '—' : items.length}} 个{{topics?'专题':'课程'}}</span>
    <div v-if="topics&&detail" class="topic-feature-courses"><span>关联课程</span><RouterLink v-for="n in detail.related" :key="n.id" :to="`/course-resources/${n.id}`">{{n.name}} <small>{{n.resourceCount}} 份</small></RouterLink><span v-if="!detail.related.length">暂无已发布关联课程</span><span class="topic-resource-total">{{detail.item.resourceCount}} 份已发布资源</span></div>
  </section>
  <p v-if="error" class="message error" role="alert">{{error}}</p><LoadingState v-if="loading"/>
  <template v-else-if="detail">
    <section v-if="!topics" class="card navigation-detail"><span class="file-icon"><AppIcon name="course" :size="28"/></span><div><h2>课程信息</h2><p>{{detail.item.code}} · {{detail.item.resourceCount}} 份已发布资源</p><div class="tag-list"><span class="muted">相关思政元素</span><RouterLink v-for="n in detail.related" :key="n.id" class="tag" :to="`/ideological-topics/${n.id}`">{{n.name}} · {{n.resourceCount}}</RouterLink><span v-if="!detail.related.length" class="muted">暂无已发布关联</span></div></div></section>
    <div class="section-heading"><h2>{{topics?'专题教学资源':'课程教学资源'}}</h2><span class="muted">仅展示审核通过且已发布的资源</span></div>
    <ResourceLibraryView :key="route.fullPath" embedded :fixed-course="topics?undefined:route.params.id" :fixed-element="topics?route.params.id:undefined"/>
  </template>
  <template v-else-if="!error">
    <section class="navigation-search"><AppIcon name="search"/><input v-model="keyword" :aria-label="topics?'检索专题':'检索课程'" :placeholder="topics?'输入元素名称或说明':'输入课程编号、名称或简介'"/></section>
    <div v-if="topics" class="topic-directory">
      <RouterLink v-for="(n,index) in filtered" :key="n.id" :to="`${base}/${n.id}`" class="topic-directory-entry"><span class="topic-ordinal" aria-hidden="true">{{String(index+1).padStart(2,'0')}}</span><div><h2>{{n.name}}</h2><p>{{n.description||'围绕该元素查阅课程教学材料。'}}</p><footer><span><strong>{{n.resourceCount}}</strong> 份已发布资源</span><span>进入专题 <AppIcon name="arrow" :size="17"/></span></footer></div></RouterLink>
    </div>
    <div v-else class="course-directory">
      <article v-for="(n,index) in filtered" :key="n.id" class="course-index-entry">
        <div class="course-index-number"><span>{{String(index+1).padStart(2,'0')}}</span><small>{{n.code}}</small></div>
        <div class="course-index-content"><h2><RouterLink :to="`${base}/${n.id}`">{{n.name}}</RouterLink></h2><p>{{n.description||'暂无课程简介。'}}</p><div class="tag-list course-index-tags"><span v-if="courseRelations[n.id]?.loading" class="field-hint">正在读取关联元素…</span><span v-else-if="courseRelations[n.id]?.error" class="field-hint">关联元素暂未加载，可进入课程详情查看。</span><template v-else><RouterLink v-for="e in (courseRelations[n.id]?.items||[]).slice(0,3)" :key="e.id" class="tag" :to="`/ideological-topics/${e.id}`">{{e.name}}</RouterLink><span v-if="courseRelations[n.id]?.items?.length>3" class="field-hint">另 {{courseRelations[n.id].items.length-3}} 项，见课程详情</span><span v-else-if="courseRelations[n.id]?.items?.length===0" class="field-hint">暂无已发布资源关联元素</span></template></div></div>
        <RouterLink :to="`${base}/${n.id}`" class="course-index-count" :aria-label="`${n.name}，${n.resourceCount}份资源，查看课程`"><strong>{{n.resourceCount}}</strong><span>份资源</span><AppIcon name="arrow" :size="16"/></RouterLink>
      </article>
    </div><EmptyState v-if="!filtered.length" title="暂无符合条件的导航内容" description="这里仅展示启用课程或启用思政元素。"/>
  </template>
</template>

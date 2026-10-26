<script setup>
import { computed, inject, ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publishedResourceApi } from '../api/resources.js'
import { getCourseOptions, getResourceCategoryOptions, getIdeologicalElementOptions } from '../api/baseData.js'
import ResourceCard from '../components/ResourceCard.vue'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
import AppIcon from '../components/AppIcon.vue'
import FavoriteFolders from '../components/FavoriteFolders.vue'
import {folderApi} from '../api/enhancements.js'
import { defaultResourceFilters, normalizeResourceFilters, resourceFilterPage, resourceFilterQuery, resourceFilterParams, removeResourceFilter, resourceFilterChips } from '../utils/resourceFilters.js'
const folders=ref([])
const props=defineProps({ favoritesOnly:Boolean, fixedCourse:[String,Number], fixedElement:[String,Number], embedded:Boolean })
const studentPresentation = inject('studentPortalPresentation', false)
const defaults=defaultResourceFilters
const applied=ref(defaults())
const route=useRoute(), router=useRouter(), filters=reactive(defaults()), courses=ref([]),categories=ref([]),elements=ref([]),items=ref([]),loading=ref(true),error=ref(''),page=ref(1),total=ref(0),changing=ref(0),size=9
const pages=computed(()=>Math.max(1,Math.ceil(total.value/size)))
const selected=computed(()=>resourceFilterChips(applied.value,{courses:courses.value,categories:categories.value,elements:elements.value,folders:folders.value},props))
const hasPendingChanges=computed(()=>JSON.stringify(normalizeResourceFilters(filters,props))!==JSON.stringify(applied.value))
let sequence=0, ownNavigation=null, previousContext=''
async function load(){const current=++sequence;loading.value=true;error.value='';try{const params=resourceFilterParams(applied.value,page.value,size,props);const result=await(props.favoritesOnly?publishedResourceApi.favorites(params):publishedResourceApi.list(params));if(current!==sequence)return;items.value=result.items;total.value=result.total;if(page.value>pages.value)await apply(applied.value,pages.value)}catch(e){if(current===sequence)error.value=e.message}finally{if(current===sequence)loading.value=false}}
async function apply(values,nextPage=1){
  const next=normalizeResourceFilters(values,props)
  if(props.embedded){applied.value=next;page.value=resourceFilterPage(nextPage);await load();return}
  const query=resourceFilterQuery(next,nextPage,props),target=router.resolve({path:route.path,query}).fullPath
  if(target===route.fullPath){applied.value=next;page.value=resourceFilterPage(nextPage);await load();return}
  // Local chip/paging actions must not submit or discard other unfinished inputs.
  ownNavigation=target
  try{await router.replace({path:route.path,query})}catch(e){error.value=e.message}finally{if(ownNavigation===target)ownNavigation=null}
}
function search(){Object.assign(filters,normalizeResourceFilters(filters,props));return apply(filters)}
function reset(){Object.assign(filters,defaults());return apply(filters)}
function removeFilter(key){filters[key]=defaults()[key];return apply(removeResourceFilter(applied.value,key,props))}
function selectFolder(folderId){filters.folderId=folderId;return apply({...applied.value,folderId})}
async function favorite(r){if(changing.value)return;changing.value=r.id;try{r.favorite=(await publishedResourceApi.favorite(r.id,!r.favorite)).favorite;if(props.favoritesOnly)await load()}catch(e){error.value=e.message}finally{changing.value=0}}
async function move(r,value){if(changing.value)return;changing.value=r.id;try{await folderApi.move(r.id,value?Number(value):null);await load()}catch(e){error.value=e.message}finally{changing.value=0}}
watch(()=>[route.path,route.query,props.favoritesOnly,props.fixedCourse,props.fixedElement],()=>{
  const context=JSON.stringify([route.path,props.favoritesOnly,props.fixedCourse,props.fixedElement])
  const keepDraft=ownNavigation===route.fullPath&&context===previousContext
  previousContext=context;ownNavigation=null
  applied.value=normalizeResourceFilters(props.embedded?{}:route.query,props)
  page.value=props.embedded?1:resourceFilterPage(route.query.page)
  if(!keepDraft)Object.assign(filters,applied.value)
  load()
},{immediate:true})
onMounted(async()=>{try{[courses.value,categories.value,elements.value]=await Promise.all([getCourseOptions(),getResourceCategoryOptions(),getIdeologicalElementOptions()])}catch(e){error.value=e.message}})
onBeforeUnmount(()=>sequence++)
</script>
<template><section v-if="!embedded" class="page-heading heading-row"><div><p class="eyebrow">资源发现与内部共享</p><h1>{{favoritesOnly?'我的收藏':'资源中心'}}</h1><p class="muted">{{favoritesOnly?'已收藏且当前仍已发布的教学资源，便于持续查阅。':'按课程、分类和课程思政元素发现已审核发布的教学资源。'}}</p></div><span class="count-pill">{{loading || error ? '—' : total}} 份资源</span></section>
<FavoriteFolders v-if="favoritesOnly" :selected="applied.folderId" @select="selectFolder" @loaded="folders=$event" @changed="load"/>
<section class="card filter-card"><form @submit.prevent="search"><div class="filter-row"><div><label for="library-keyword">关键词</label><input id="library-keyword" v-model="filters.keyword" placeholder="资源标题或简介" maxlength="200"/></div><div v-if="!fixedCourse"><label for="library-course">课程</label><select id="library-course" v-model="filters.courseId"><option value="">全部课程</option><option v-for="c in courses" :key="c.id" :value="c.id">{{c.name}}</option></select></div><div><label for="library-category">资源分类</label><select id="library-category" v-model="filters.categoryId"><option value="">全部分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{c.name}}</option></select></div><div v-if="!fixedElement"><label for="library-element">课程思政元素</label><select id="library-element" v-model="filters.elementId"><option value="">全部元素</option><option v-for="e in elements" :key="e.id" :value="e.id">{{e.name}}</option></select></div></div>
<div class="filter-row library-extra-filters"><div><label for="library-format">文件格式</label><select id="library-format" v-model="filters.format"><option value="">全部格式</option><option value="PDF">PDF文档</option><option value="IMAGE">图片</option><option value="OFFICE">Office文档</option></select></div><div><label for="library-sort">排序</label><select id="library-sort" v-model="filters.sort"><option value="NEWEST">最新发布</option><option value="OLDEST">最早发布</option><option value="TITLE">标题升序</option></select></div><div><label for="library-from">发布起始日期</label><input id="library-from" v-model="filters.from" type="date" :max="filters.to||undefined"/></div><div><label for="library-to">发布截止日期</label><input id="library-to" v-model="filters.to" type="date" :min="filters.from||undefined"/></div><button class="primary" :disabled="loading"><AppIcon name="search" :size="17"/>查询</button><button type="button" class="secondary" :disabled="loading" @click="reset">重置</button></div></form>
<div v-if="selected.length" class="applied-filters" aria-label="已应用的筛选条件">
  <div class="applied-filter-heading"><strong>已应用 {{selected.length}} 项条件</strong><button type="button" class="text-button" :disabled="loading" @click="reset">清除全部</button></div>
  <ul class="applied-filter-list"><li v-for="tag in selected" :key="tag.key"><button type="button" class="applied-filter-chip" :disabled="loading" :aria-label="`清除${tag.label}`" @click="removeFilter(tag.key)"><span>{{tag.label}}</span><AppIcon name="close" :size="14"/></button></li></ul>
</div>
<p v-if="hasPendingChanges" class="pending-filter-note" role="status">表单有尚未应用的调整，点击“查询”后生效。清除条件或翻页不会提交这些调整。</p>
</section>
  <p v-if="error" class="message error" role="alert">{{error}}</p><LoadingState v-if="loading"/><div v-else-if="!error" class="resource-card-grid"><ResourceCard v-for="r in items" :key="r.id" :resource="r" :keyword="applied.keyword" :changing="!!changing" :visual-cover="studentPresentation" :folders="favoritesOnly?folders:undefined" @favorite="favorite" @move="move"/></div><EmptyState v-if="!loading&&!error&&!items.length" :title="favoritesOnly?'暂无有效收藏':'没有符合条件的资源'" :description="favoritesOnly?'在资源中心收藏已发布资源后，可在这里快速访问。':'请调整或重置筛选条件，未审核资源不会出现在这里。'"/>
<div class="pagination"><span>共 {{loading || error ? '—' : total}} 条 · 第 {{page}} / {{pages}} 页</span><div><button class="secondary" :disabled="loading||!!error||page<=1" @click="apply(applied,page-1)">上一页</button><button class="secondary" :disabled="loading||!!error||page>=pages" @click="apply(applied,page+1)">下一页</button></div></div></template>
<style scoped>
.library-extra-filters { margin-top:14px; padding-top:14px; border-top:1px solid var(--line); }
.library-extra-filters>div:first-child { flex:1; min-width:145px; }
.applied-filters { margin-top:18px; padding-top:14px; border-top:1px solid var(--line); }
.applied-filter-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:10px; font-size:12px; color:var(--muted); }
.applied-filter-list { display:flex; flex-wrap:wrap; gap:8px; margin:0; padding:0; list-style:none; }
.applied-filter-list li { min-width:0; max-width:100%; }
.applied-filter-chip { display:inline-flex; align-items:center; gap:8px; max-width:100%; padding:7px 10px; border:1px solid var(--line); border-radius:7px; background:var(--surface); color:var(--ink); font-size:12px; text-align:left; }
.applied-filter-chip span { min-width:0; overflow-wrap:anywhere; }
.applied-filter-chip svg { flex-shrink:0; color:var(--muted); }
.applied-filter-chip:hover:not(:disabled) { border-color:var(--primary); color:var(--primary); }
.applied-filter-chip:focus-visible { outline:2px solid var(--primary); outline-offset:3px; }
.pending-filter-note { margin:12px 0 0; font-size:12px; line-height:1.7; color:var(--muted); }
@media(max-width:760px){.library-extra-filters>div:first-child{min-width:100%;}}
</style>

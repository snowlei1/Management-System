<script setup>
import {computed,nextTick,onMounted,reactive,ref} from 'vue'
import {governanceApi} from '../api/enhancements.js'
import {timeText} from '../api/resourceState.js'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
const props=defineProps({management:{type:Boolean,default:false}})
const kinds={FILE:'附件 / 预览',CONTENT:'内容',CLASSIFICATION:'分类 / 标注',RIGHTS:'来源 / 授权',OTHER:'其他'},states={OPEN:'待处理',RESOLVED:'已处理',DISMISSED:'不予采纳'}
const status=ref('ALL'),page=ref(1),size=10,items=ref([]),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),selected=ref(null),form=reactive({status:'RESOLVED',response:''}),notice=ref('')
const pages=computed(()=>Math.max(1,Math.ceil(total.value/size)))
const handling=ref(null)
let sequence=0
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const r=await governanceApi.feedback(props.management,{status:status.value,page:page.value,size});if(seq===sequence){items.value=r.items;total.value=r.total}}catch(e){if(seq===sequence)error.value=e.message}finally{if(seq===sequence)loading.value=false}}
async function select(row){selected.value=row;form.status='RESOLVED';form.response='';error.value='';notice.value='';await nextTick();handling.value?.focus({preventScroll:true});handling.value?.scrollIntoView({block:'start'})}
async function handle(){if(busy.value||!selected.value)return;busy.value=true;error.value='';try{await governanceApi.handle(selected.value.id,form);selected.value=null;notice.value='处理结果已保存，反馈人可查看回复。';await load()}catch(e){error.value=e.message}finally{busy.value=false}}
onMounted(load)
</script>
<template>
  <section class="page-heading"><p class="eyebrow">{{management?'管理员 · 资源治理':'资源使用 · 私人反馈'}}</p><h1>{{management?'资源问题反馈':'我的反馈'}}</h1><p class="muted">{{management?'核查资源问题并记录处理说明；反馈处理不会自动改变资源发布状态。':'仅本人和管理员可查看，资源下架后仍保留处理记录。'}}</p></section>
  <p v-if="notice" class="message success" role="status">{{notice}}</p><p v-if="error" class="message error" role="alert">{{error}}</p>
  <section class="card"><form class="filter-row" @submit.prevent="page=1;load()"><div><label for="feedback-status">处理状态</label><select id="feedback-status" v-model="status"><option value="ALL">全部状态</option><option v-for="(label,key) in states" :key="key" :value="key">{{label}}</option></select></div><button class="primary" :disabled="loading||busy">查询</button></form>
    <LoadingState v-if="loading"/><div v-else class="table-wrap"><table><thead><tr><th>资源 / 问题类型</th><th>问题说明</th><th v-if="management">反馈人</th><th>状态 / 提交时间</th><th>处理结果</th><th v-if="management">操作</th></tr></thead><tbody><tr v-for="row in items" :key="row.id"><td class="base-name"><strong>{{row.title}}</strong><small class="description-cell">{{kinds[row.kind]}} · #{{row.id}}</small></td><td class="feedback-text preserve-lines">{{row.description}}</td><td v-if="management">{{row.reporterName}}</td><td><span class="badge" :class="row.status==='OPEN'?'state-PENDING':'state-APPROVED'">{{states[row.status]}}</span><small class="description-cell">{{timeText(row.createdAt)}}</small></td><td class="feedback-text"><template v-if="row.handledAt"><p class="preserve-lines">{{row.response}}</p><small>{{row.handlerName}} · {{timeText(row.handledAt)}}</small></template><span v-else class="muted">等待管理员核查</span></td><td v-if="management"><button v-if="row.status==='OPEN'" class="secondary" :disabled="busy" @click="select(row)">处理反馈</button><span v-else class="muted">已归档</span></td></tr></tbody></table><EmptyState v-if="!items.length" title="暂无对应反馈"/></div>
    <div class="pagination"><span>共 {{total}} 条 · 第 {{page}} / {{pages}} 页</span><div><button class="secondary" :disabled="page<=1||loading||busy" @click="page--;load()">上一页</button><button class="secondary" :disabled="page>=pages||loading||busy" @click="page++;load()">下一页</button></div></div>
  </section>
  <section v-if="selected" ref="handling" tabindex="-1" class="feedback-handling" style="scroll-margin-top:96px"><div class="section-heading"><h2>处理反馈 #{{selected.id}}</h2><button class="secondary" :disabled="busy" @click="selected=null">取消</button></div><h3>{{selected.title}}</h3><p class="preserve-lines">{{selected.description}}</p><form @submit.prevent="handle"><label for="handle-status">处理结论</label><select id="handle-status" v-model="form.status" :disabled="busy"><option value="RESOLVED">已处理</option><option value="DISMISSED">不予采纳</option></select><label for="handle-response">处理说明（必填）</label><textarea id="handle-response" v-model="form.response" required maxlength="1000" rows="4" :disabled="busy"/><p class="field-hint">保存后不可覆盖原结论。若资源需要下架，请在资源台账单独操作。</p><button class="primary" :disabled="busy||!form.response.trim()">{{busy?'保存中…':'确认保存处理结果'}}</button></form></section>
</template>
<style scoped>
.feedback-text{min-width:180px;max-width:360px;overflow-wrap:anywhere}.feedback-handling{margin-top:24px;border-top:3px solid #b3914b;padding:24px;background:var(--surface,#fff)}.feedback-handling form{max-width:720px;display:grid;gap:10px}.feedback-handling button{justify-self:start}.feedback-handling select{max-width:240px}
</style>

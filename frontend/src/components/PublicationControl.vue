<script setup>
import {nextTick,ref,watch} from 'vue'
import {governanceApi} from '../api/enhancements.js'
import {timeText} from '../api/resourceState.js'
const props=defineProps({resourceId:{type:Number,required:true}}),emit=defineEmits(['changed','close'])
const data=ref(null),busy=ref(false),loading=ref(false),reason=ref(''),error=ref(''),success=ref('')
const panel=ref(null)
let sequence=0
watch(()=>props.resourceId,async id=>{const seq=++sequence;data.value=null;reason.value='';error.value='';success.value='';loading.value=true;try{const r=await governanceApi.publication(id);if(seq===sequence){data.value=r;await nextTick();if(seq===sequence){panel.value?.focus({preventScroll:true});panel.value?.scrollIntoView({block:'start'})}}}catch(e){if(seq===sequence)error.value=e.message}finally{if(seq===sequence)loading.value=false}},{immediate:true})
async function change(){
  if(busy.value||!data.value)return
  const id=props.resourceId,seq=sequence,down=!data.value.withdrawnAt
  busy.value=true;error.value='';success.value=''
  try{const result=await governanceApi.change(id,down,reason.value);if(seq===sequence){data.value=result;reason.value='';success.value=down?'资源已下架，普通用户不可再检索或使用。':'资源已恢复发布，原文件和审核历史保持不变。';emit('changed')}}
  catch(e){if(seq===sequence)error.value=e.message}
  finally{busy.value=false}
}
</script>
<template>
  <section ref="panel" tabindex="-1" class="publication-control" style="scroll-margin-top:96px" aria-label="资源发布管理">
    <div class="section-heading"><h2>发布管理</h2><button class="secondary" :disabled="busy" @click="emit('close')">关闭</button></div>
    <p v-if="loading" role="status">正在读取发布记录…</p><p v-if="error" class="message error" role="alert">{{error}}</p><p v-if="success" class="message success" role="status">{{success}}</p>
    <template v-if="data"><h3>{{data.title}}</h3><p><span class="badge" :class="data.withdrawnAt?'state-REJECTED':'state-APPROVED'">{{data.withdrawnAt?'已下架':'已发布'}}</span></p>
      <p class="field-hint">下架只暂停系统内使用，保留附件、审核记录、收藏关系和使用历史；恢复不改动原内容，也不会重新审核。</p>
      <form class="publication-form" @submit.prevent="change"><label for="publication-reason">{{data.withdrawnAt?'恢复说明（可选）':'下架原因（必填）'}}</label><textarea id="publication-reason" v-model="reason" maxlength="1000" rows="3" :required="!data.withdrawnAt" :disabled="busy"/><button :class="data.withdrawnAt?'primary':'danger'" :disabled="busy||(!data.withdrawnAt&&!reason.trim())">{{busy?'处理中…':data.withdrawnAt?'确认恢复发布':'确认下架资源'}}</button></form>
      <h3>发布操作记录</h3><ol class="publication-events"><li v-for="event in data.events" :key="event.id"><strong>{{event.action==='WITHDRAW'?'下架':'恢复发布'}}</strong><small>{{event.actorName}} · {{timeText(event.occurredAt)}}</small><p class="preserve-lines">{{event.reason||'未填写恢复说明'}}</p></li></ol><p v-if="!data.events.length" class="muted">尚无下架或恢复记录；原审核结论请查看审核历史。</p>
    </template>
  </section>
</template>
<style scoped>
.publication-control{padding:24px;background:var(--surface,#fffdfa);border:1px solid var(--border,#e5e2dc);border-left:3px solid #b3914b;border-radius:8px;margin-top:20px}.publication-form{display:grid;gap:10px;max-width:720px;margin:20px 0}.publication-form button{justify-self:start}.publication-events{padding-left:22px}.publication-events li{padding:8px 0;border-bottom:1px solid var(--border,#e5e2dc)}.publication-events small{display:block;color:var(--text-muted,#746f68);margin:5px 0}
</style>

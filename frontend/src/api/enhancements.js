import {api} from './client.js'
const query = params => new URLSearchParams(Object.entries(params).filter(([,v])=>v!==''&&v!=null)).toString()
export const governanceApi = {
  publication:id=>api(`/admin/published-resources/${id}/publication`),
  change:(id,withdraw,reason)=>api(`/admin/published-resources/${id}/${withdraw?'withdraw':'restore'}`,{method:'POST',body:{reason}}),
  report:(id,body)=>api(`/resources/${id}/feedback`,{method:'POST',body}),
  feedback:(admin,params)=>api(`${admin?'/admin/resource-feedback':'/resource-feedback/mine'}?${query(params)}`),
  handle:(id,body)=>api(`/admin/resource-feedback/${id}/handle`,{method:'POST',body}),
}
export const folderApi={list:()=>api('/favorite-folders'),create:name=>api('/favorite-folders',{method:'POST',body:{name}}),rename:(id,name)=>api(`/favorite-folders/${id}`,{method:'PUT',body:{name}}),remove:id=>api(`/favorite-folders/${id}`,{method:'DELETE'}),move:(id,folderId)=>api(`/favorites/${id}/folder`,{method:'PUT',body:{folderId}})}
export const supplementApi={read:(id,scope='public')=>api(`${scope==='teacher'?'/teacher/resources':scope==='review'?'/admin/resource-reviews':'/resources'}/${id}/metadata`),save:(id,body)=>api(`/teacher/resources/${id}/metadata`,{method:'PUT',body}),cover:(id,file)=>{const body=new FormData();body.append('file',file);return api(`/teacher/resources/${id}/cover`,{method:'PUT',body})},removeCover:id=>api(`/teacher/resources/${id}/cover`,{method:'DELETE'})}

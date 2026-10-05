import { api } from './client.js'

function multipart(metadata, file) {
  const body = new FormData()
  body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  if (file) body.append('file', file)
  return body
}
export const resourceDraftApi = {
  list: filters => api(`/teacher/resources?${new URLSearchParams(filters)}`),
  detail: id => api(`/teacher/resources/${id}`),
  policy: () => api('/teacher/resources/upload-policy'),
  create: (metadata, file) => api('/teacher/resources', { method: 'POST', body: multipart(metadata, file) }),
  update: (id, metadata, file) => api(`/teacher/resources/${id}`, { method: 'PUT', body: multipart(metadata, file) }),
  remove: id => api(`/teacher/resources/${id}`, { method: 'DELETE' }),
  submit: id => api(`/teacher/resources/${id}/submit`, { method: 'POST' }),
}
export const resourceReviewApi = {
  list: filters => api(`/admin/resource-reviews?${new URLSearchParams(filters)}`),
  detail: id => api(`/admin/resource-reviews/${id}`),
  attachment: id => api(`/admin/resource-reviews/${id}/attachment`, { responseType: 'blob' }),
  approve: (id, submissionNo) => api(`/admin/resource-reviews/${id}/approve`, { method: 'POST', body: { submissionNo } }),
  reject: (id, submissionNo, reason) => api(`/admin/resource-reviews/${id}/reject`, { method: 'POST', body: { submissionNo, reason } }),
}
export const publishedResourceApi = {
  list: filters => api(`/resources?${new URLSearchParams(filters)}`),
  favorites: filters => api(`/favorites?${new URLSearchParams(filters)}`),
  detail: id => api(`/resources/${id}`),
  preview: id => api(`/resources/${id}/preview`, { responseType: 'blob' }),
  download: id => api(`/resources/${id}/download`, { responseType: 'blob' }),
  favorite: (id, active) => api(`/resources/${id}/favorite`, { method: active ? 'POST' : 'DELETE' }),
}

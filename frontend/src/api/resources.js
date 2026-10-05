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
}

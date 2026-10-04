import { api } from './client.js'

const types = new Set(['courses', 'ideological-elements', 'resource-categories'])
function endpoint(type) {
  if (!types.has(type)) throw new Error('基础数据类型不正确')
  return `/${type}`
}

export const baseDataApi = {
  list(type, filters) { return api(`${endpoint(type)}?${new URLSearchParams(filters)}`) },
  create(type, body) { return api(endpoint(type), { method: 'POST', body }) },
  update(type, id, body) { return api(`${endpoint(type)}/${id}`, { method: 'PUT', body }) },
  setStatus(type, id, status) { return api(`${endpoint(type)}/${id}/status`, { method: 'PATCH', body: { status } }) },
  options(type) { return api(`/options${endpoint(type)}`) },
}

export const getCourseOptions = () => baseDataApi.options('courses')
export const getIdeologicalElementOptions = () => baseDataApi.options('ideological-elements')
export const getResourceCategoryOptions = () => baseDataApi.options('resource-categories')

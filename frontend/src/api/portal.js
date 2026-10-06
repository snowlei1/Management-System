import { api } from './client.js'
export const portalApi = {
  courses: () => api('/portal/courses'), topics: () => api('/portal/ideological-topics'),
  navigation: (topics, id) => api(`/portal/${topics ? 'ideological-topics' : 'courses'}/${id}`),
  presentation: id => api(`/portal/resources/${id}`),
  history: (kind, filters = {}) => api(`/history/${kind}?${new URLSearchParams(filters)}`),
  dashboard: () => api('/teacher/resource-dashboard'),
  ownPresentations: ids => ids.length ? api(`/teacher/resource-presentations?ids=${ids.join(',')}`) : Promise.resolve([]),
  ledger: filters => api(`/admin/published-resources?${new URLSearchParams(filters)}`),
  pendingRecent: () => api('/admin/review-overview'),
}

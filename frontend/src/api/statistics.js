import { api } from './client.js'

export const getStatistics = () => api('/admin/statistics/overview')

// Keep editable inputs separate from the query that produced the current results.
export function defaultResourceFilters() {
  return { keyword: '', courseId: '', categoryId: '', elementId: '', format: '', sort: 'NEWEST', from: '', to: '', folderId: '' }
}

export function normalizeResourceFilters(source = {}, scope = {}) {
  const filters = defaultResourceFilters()
  for (const key of Object.keys(filters)) {
    const value = Array.isArray(source[key]) ? source[key][0] : source[key]
    if (value != null) filters[key] = String(value).trim()
  }
  if (!filters.sort) filters.sort = 'NEWEST'
  if (scope.fixedCourse) filters.courseId = ''
  if (scope.fixedElement) filters.elementId = ''
  if (!scope.favoritesOnly) filters.folderId = ''
  return filters
}

export function resourceFilterPage(value) {
  const page = Number(Array.isArray(value) ? value[0] : value)
  return Number.isSafeInteger(page) && page > 0 ? page : 1
}

export function resourceFilterQuery(source, page = 1, scope = {}) {
  const query = {}
  for (const [key, value] of Object.entries(normalizeResourceFilters(source, scope))) {
    if (value && !(key === 'sort' && value === 'NEWEST')) query[key] = value
  }
  if (resourceFilterPage(page) > 1) query.page = String(page)
  return query
}

export function resourceFilterParams(source, page, size, scope = {}) {
  const params = { ...resourceFilterQuery(source, page, scope), page: resourceFilterPage(page), size }
  if (scope.fixedCourse) params.courseId = String(scope.fixedCourse)
  if (scope.fixedElement) params.elementId = String(scope.fixedElement)
  return params
}

export function removeResourceFilter(source, key, scope = {}) {
  const next = normalizeResourceFilters(source, scope)
  if (Object.hasOwn(next, key)) next[key] = defaultResourceFilters()[key]
  return next
}

export function resourceFilterChips(source, options = {}, scope = {}) {
  const filters = normalizeResourceFilters(source, scope)
  const optionName = (key, value) => options[key]?.find(item => String(item.id) === value)?.name || `编号 ${value}`
  const labels = {
    keyword: value => `关键词：${value}`,
    courseId: value => `课程：${optionName('courses', value)}`,
    categoryId: value => `分类：${optionName('categories', value)}`,
    elementId: value => `思政元素：${optionName('elements', value)}`,
    format: value => `格式：${({ PDF: 'PDF文档', IMAGE: '图片', OFFICE: 'Office文档' })[value] || value}`,
    sort: value => `排序：${({ OLDEST: '最早发布', TITLE: '标题升序' })[value] || value}`,
    from: value => `发布起始：${value}`,
    to: value => `发布截止：${value}`,
    folderId: value => `收藏夹：${value === '0' ? '未分组' : optionName('folders', value)}`
  }
  return Object.entries(filters)
    .filter(([key, value]) => value && !(key === 'sort' && value === 'NEWEST'))
    .map(([key, value]) => ({ key, label: labels[key](value) }))
}

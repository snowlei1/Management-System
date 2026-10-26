import test from 'node:test'
import assert from 'node:assert/strict'
import { defaultResourceFilters, normalizeResourceFilters, resourceFilterPage, resourceFilterQuery, resourceFilterParams, removeResourceFilter, resourceFilterChips } from '../src/utils/resourceFilters.js'

test('defaults produce no removable chips or URL parameters', () => {
  assert.deepEqual(resourceFilterChips(defaultResourceFilters()), [])
  assert.deepEqual(resourceFilterQuery({}), {})
})
test('normalization trims values without mutating the draft', () => {
  const input = { keyword: ' 工程伦理 ', categoryId: 3 }
  assert.equal(normalizeResourceFilters(input).keyword, '工程伦理')
  assert.equal(normalizeResourceFilters(input).categoryId, '3')
  assert.equal(input.keyword, ' 工程伦理 ')
})
test('repeated URL fields use first value; unrelated fields are excluded', () => {
  assert.deepEqual(resourceFilterQuery({ keyword: ['课程', '其他'], extra: 'ignored' }), { keyword: '课程' })
})
test('whitespace and blank sort normalize to defaults', () => {
  assert.deepEqual(resourceFilterQuery({ keyword: '  ', sort: '' }), {})
})
test('page parsing rejects negative, fractional, infinite and unsafe values', () => {
  for (const value of [undefined, '', 'oops', '-1', '1.5', Infinity, 9007199254740992]) assert.equal(resourceFilterPage(value), 1)
  assert.equal(resourceFilterPage(['3', '8']), 3)
})
test('query round-trip retains filters and pagination', () => {
  const source = { keyword: '数字 教学', courseId: '3', sort: 'TITLE', format: 'PDF', from: '2026-01-01', to: '2026-10-23' }
  const query = resourceFilterQuery(source, 4)
  assert.equal(query.page, '4')
  assert.deepEqual(normalizeResourceFilters(query), normalizeResourceFilters(source))
})
test('single removal preserves other applied fields and resets to page one', () => {
  const source = { keyword: '伦理', format: 'PDF', sort: 'TITLE', page: '5' }
  assert.deepEqual(resourceFilterQuery(removeResourceFilter(source, 'format')), { keyword: '伦理', sort: 'TITLE' })
  assert.equal(source.format, 'PDF')
})
test('removing sort restores NEWEST', () => {
  assert.equal(removeResourceFilter({ sort: 'OLDEST' }, 'sort').sort, 'NEWEST')
  assert.deepEqual(resourceFilterQuery(removeResourceFilter({ sort: 'OLDEST' }, 'sort')), {})
})
test('unknown removal key cannot add properties', () => {
  assert.deepEqual(removeResourceFilter({ keyword: '课程' }, '__proto__'), normalizeResourceFilters({ keyword: '课程' }))
})
test('fixed course scope cannot be overridden, displayed as removable or cleared', () => {
  const scope = { fixedCourse: 7, embedded: true }
  assert.deepEqual(resourceFilterChips({ courseId: 99 }, {}, scope), [])
  assert.equal(resourceFilterParams({}, 1, 9, scope).courseId, '7')
  assert.equal(resourceFilterParams({ courseId: 99 }, 1, 9, scope).courseId, '7')
})
test('fixed topic scope survives reset and is not removable', () => {
  const scope = { fixedElement: '8', embedded: true }
  assert.deepEqual(resourceFilterChips({ elementId: '99' }, {}, scope), [])
  assert.equal(resourceFilterParams(defaultResourceFilters(), 1, 9, scope).elementId, '8')
})
test('folders never leak into ordinary searches', () => {
  assert.deepEqual(resourceFilterQuery({ folderId: '6' }), {})
  assert.deepEqual(resourceFilterChips({ folderId: '6' }), [])
})
test('ungrouped folder zero remains active and can be removed', () => {
  const scope = { favoritesOnly: true }
  assert.deepEqual(resourceFilterQuery({ folderId: '0' }, 1, scope), { folderId: '0' })
  assert.deepEqual(resourceFilterChips({ folderId: '0' }, {}, scope), [{ key: 'folderId', label: '收藏夹：未分组' }])
  assert.deepEqual(resourceFilterQuery(removeResourceFilter({ folderId: '0' }, 'folderId', scope), 1, scope), {})
})
test('options and folders use real names and typed labels', () => {
  const options = { courses: [{ id: 1, name: '软件工程' }], categories: [{ id: 2, name: '案例' }], elements: [{ id: 3, name: '职业责任' }], folders: [{ id: 4, name: '备课资料' }] }
  assert.deepEqual(resourceFilterChips({ courseId: 1, categoryId: 2, elementId: 3, folderId: 4 }, options, { favoritesOnly: true }).map(c => c.label), ['课程：软件工程', '分类：案例', '思政元素：职业责任', '收藏夹：备课资料'])
})
test('missing options retain truthful fallback and labels update after loading', () => {
  assert.equal(resourceFilterChips({ courseId: '88' })[0].label, '课程：编号 88')
  assert.equal(resourceFilterChips({ courseId: '88' }, { courses: [{ id: 88, name: '已加载课程' }] })[0].label, '课程：已加载课程')
})
test('format, dates and nondefault sort are represented', () => {
  assert.deepEqual(resourceFilterChips({ format: 'PDF', sort: 'OLDEST', from: '2026-01-01', to: '2026-10-23' }).map(c => c.key), ['format', 'sort', 'from', 'to'])
})
test('keywords remain literal text rather than markup or regular expressions', () => {
  const keyword = '<img src=x onerror=alert(1)> [a].*'
  assert.equal(resourceFilterChips({ keyword })[0].label, `关键词：${keyword}`)
})

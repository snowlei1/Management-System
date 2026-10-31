// Local development browser acceptance. Uses an isolated Edge context and its own labelled fixture.
// Never supplies credentials to a remote target. The finally block restores only this run's fixture.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFile, mkdir } from 'node:fs/promises'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const baseURL = process.env.GOVERNANCE_TEST_BASE_URL || 'http://127.0.0.1:5173'
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(baseURL).hostname), 'Only local development targets are allowed')
const output = path.resolve('tmp/enhancements/governance-browser')
await mkdir(output, { recursive: true })
const tag = randomUUID().slice(0, 8), title = `浏览器治理验收-${tag}`
const browser = await chromium.launch({ headless: true, channel: process.env.GOVERNANCE_TEST_BROWSER_CHANNEL || 'msedge' })
let checks = 0, id = 0, admin
const errors = []
function check(name, value) { assert.ok(value, name); checks++; console.log(`PASS: ${name}`) }
async function login(role) {
  const password = process.env[`GOVERNANCE_TEST_${role.toUpperCase()}_PASSWORD`]
  assert.ok(password, `Supply the local development password for ${role}`)
  const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  // Block cross-origin requests, including accidental redirects from the local application.
  await context.route('**/*', route => {
    const url = new URL(route.request().url())
    return ['http:', 'https:'].includes(url.protocol) && url.origin !== new URL(baseURL).origin ? route.abort() : route.continue()
  })
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/login')
  await page.locator('#username').fill(`dev_${role}`)
  await page.locator('#password').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => url.pathname === '/')
  check(`${role} browser login`, true)
  return { context, page }
}
async function api(actor, method, route, options = {}) {
  const csrf = (await (await actor.context.request.get('/api/auth/csrf')).json()).data
  return actor.context.request.fetch(`/api${route}`, { method, headers: { [csrf.headerName]: csrf.token }, ...options })
}
async function jsonOK(response, status = 200) {
  assert.equal(response.status(), status)
  return (await response.json()).data
}
async function changed(page, pathname, action) {
  const pending = page.waitForResponse(r => new URL(r.url()).pathname === pathname && ['POST', 'PUT'].includes(r.request().method()))
  await action()
  await jsonOK(await pending)
}
async function screenshot(page, name, top = true) {
  // Form removal and viewport changes can leave a transient scroll/compositor frame.
  // Capture a settled viewport; publication panels deliberately retain their focused position.
  await page.evaluate(top => {
    if (top) window.scrollTo({ top: 0, behavior: 'instant' })
    return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  }, top)
  await page.screenshot({ path: path.join(output, `${name}.png`), fullPage: false, animations: 'disabled' })
}
async function responsive(page, name) {
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 })
    check(`${name} no page overflow at ${width}px`, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await screenshot(page, `${name}-${width}`, name !== 'admin-publication')
  }
  await page.setViewportSize({ width: 1440, height: 900 })
}
try {
  admin = await login('admin')
  const teacher = await login('teacher'), student = await login('student')
  const options = async kind => (await jsonOK(await api(teacher, 'GET', `/options/${kind}`)))[0].id
  const courseId = await options('courses'), categoryId = await options('resource-categories'), elementId = await options('ideological-elements')
  const pdf = await readFile(path.resolve('tmp/stage6/课程思政联调案例.pdf'))
  const metadata = { title, description: '本地浏览器自动验收夹具，不代表正式教学内容。', courseId, categoryId, elementIds: [elementId] }
  const created = await jsonOK(await api(teacher, 'POST', '/teacher/resources', { multipart: {
    metadata: { name: 'metadata.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(metadata)) },
    file: { name: 'governance-browser.pdf', mimeType: 'application/pdf', buffer: pdf }
  } }), 201)
  id = created.id
  check('created this run\'s labelled draft', id > 0 && created.title === title)
  await teacher.page.goto(`/my-resources/${id}`)
  const source = teacher.page.locator(`#source-${id}`)
  await source.fill(`未保存来源-${tag}`)
  await changed(teacher.page, `/api/teacher/resources/${id}/cover`, () => teacher.page.locator(`#cover-file-${id}`).setInputFiles(path.resolve('tmp/enhancements/test-cover.png')))
  await teacher.page.locator('.cover-editor img').waitFor()
  await teacher.page.waitForFunction(id => !document.querySelector(`#source-${id}`).disabled, id)
  check('cover upload preserves unsaved source text', await source.inputValue() === `未保存来源-${tag}`)
  await teacher.page.locator(`#author-${id}`).fill('本地浏览器验收')
  await teacher.page.locator(`#rights-${id}`).fill('仅用于本机功能验收，不代表正式授权。')
  await changed(teacher.page, `/api/teacher/resources/${id}/metadata`, () => teacher.page.getByRole('button', { name: '保存来源说明', exact: true }).click())
  check('source text persisted', (await jsonOK(await api(teacher, 'GET', `/teacher/resources/${id}/metadata`))).sourceNote === `未保存来源-${tag}`)
  await screenshot(teacher.page, 'teacher-draft-metadata')
  await jsonOK(await api(teacher, 'POST', `/teacher/resources/${id}/submit`))
  await jsonOK(await api(admin, 'POST', `/admin/resource-reviews/${id}/approve`, { data: { submissionNo: 1 } }))
  const publishedAt = (await jsonOK(await api(student, 'GET', `/resources/${id}`))).publishedAt
  const originalAudits = (await jsonOK(await api(admin, 'GET', `/admin/resource-reviews/${id}`))).auditRecords
  await student.page.goto(`/resources/${id}`)
  await student.page.getByRole('heading', { name: title, exact: true }).waitFor()
  await student.page.getByRole('button', { name: '反馈问题', exact: true }).click()
  await student.page.locator('#feedback-kind').selectOption('RIGHTS')
  const description = `浏览器反馈-${tag}：请核对本地验收材料的使用说明。`
  await student.page.locator('#feedback-description').fill(description)
  await changed(student.page, `/api/resources/${id}/feedback`, () => student.page.getByRole('button', { name: '提交反馈', exact: true }).click())
  await student.page.getByRole('status').filter({ hasText: '反馈已提交' }).waitFor()
  check('student submitted feedback through visible form', true)
  await student.page.getByRole('link', { name: '我的反馈与处理结果 →', exact: true }).click()
  const studentRow = student.page.getByRole('row').filter({ hasText: description })
  await studentRow.waitFor()
  check('own feedback shows pending state', (await studentRow.textContent()).includes('待处理'))
  await screenshot(student.page, 'student-feedback-open')
  await responsive(student.page, 'student-feedback')
  const ownFeedback = (await jsonOK(await api(student, 'GET', `/resource-feedback/mine?resourceId=${id}`))).items[0]
  check('teacher cannot read student feedback', (await jsonOK(await api(teacher, 'GET', `/resource-feedback/mine?resourceId=${id}`))).total === 0)
  await teacher.page.goto('/resource-feedback')
  await teacher.page.waitForURL(url => url.pathname === '/')
  check('teacher admin route redirected', new URL(teacher.page.url()).pathname === '/')
  check('teacher admin API forbidden', (await api(teacher, 'GET', '/admin/resource-feedback')).status() === 403)
  check('student admin API forbidden', (await api(student, 'GET', '/admin/resource-feedback')).status() === 403)
  await admin.page.goto('/resource-feedback')
  const adminRow = admin.page.getByRole('row').filter({ hasText: description })
  await adminRow.getByRole('button', { name: '处理反馈', exact: true }).click()
  await admin.page.locator('#handle-status').selectOption('RESOLVED')
  const reply = `浏览器处理-${tag}：已核对本地验收材料，仅用于测试。`
  await admin.page.locator('#handle-response').fill(reply)
  await changed(admin.page, `/api/admin/resource-feedback/${ownFeedback.id}/handle`, () => admin.page.getByRole('button', { name: '确认保存处理结果', exact: true }).click())
  await admin.page.getByRole('status').filter({ hasText: '处理结果已保存' }).waitFor()
  check('admin handled through visible form', true)
  await screenshot(admin.page, 'admin-feedback-handled')
  await responsive(admin.page, 'admin-feedback')
  await student.page.reload()
  await studentRow.getByText(reply, { exact: true }).waitFor()
  const result = (await jsonOK(await api(student, 'GET', `/resource-feedback/mine?resourceId=${id}`))).items[0]
  check('student sees actual response, handler and time', result.response === reply && !!result.handlerName && !!result.handledAt)
  check('handled row has no rehandle button', await adminRow.getByRole('button', { name: '处理反馈', exact: true }).count() === 0)
  await screenshot(student.page, 'student-feedback-resolved')
  await admin.page.goto('/published-resources')
  await admin.page.locator('#ledger-keyword').fill(tag)
  const ledger = admin.page.waitForResponse(r => new URL(r.url()).pathname === '/api/admin/published-resources' && new URL(r.url()).searchParams.get('keyword') === tag)
  await admin.page.getByRole('button', { name: '查询', exact: true }).click()
  await jsonOK(await ledger)
  await admin.page.getByRole('row').filter({ hasText: title }).getByRole('button', { name: '发布管理', exact: true }).click()
  const panel = admin.page.getByRole('region', { name: '资源发布管理', exact: true })
  await panel.getByRole('button', { name: '确认下架资源', exact: true }).waitFor()
  check('withdrawal disabled without a reason', await panel.getByRole('button', { name: '确认下架资源', exact: true }).isDisabled())
  await admin.page.locator('#publication-reason').fill(`浏览器下架验收-${tag}`)
  await changed(admin.page, `/api/admin/published-resources/${id}/withdraw`, () => panel.getByRole('button', { name: '确认下架资源', exact: true }).click())
  await panel.getByRole('status').filter({ hasText: '资源已下架' }).waitFor()
  check('admin sees real withdrawal event', await panel.locator('.publication-events li').count() === 1)
  await panel.scrollIntoViewIfNeeded()
  await screenshot(admin.page, 'admin-publication-withdrawn', false)
  await responsive(admin.page, 'admin-publication')
  await student.page.goto(`/resources/${id}`)
  await student.page.getByRole('alert').first().waitFor()
  check('withdrawn resource has no feedback action', await student.page.getByRole('button', { name: '反馈问题', exact: true }).count() === 0)
  check('withdrawn download unavailable', (await api(student, 'GET', `/resources/${id}/download`)).status() === 404)
  await teacher.page.goto(`/my-resources/${id}`)
  await teacher.page.getByText('该资源已由管理员下架', { exact: false }).waitFor()
  check('teacher cannot edit withdrawn approved body or metadata', await teacher.page.getByRole('link', { name: '编辑资源', exact: true }).count() === 0 && await teacher.page.locator(`#source-${id}`).count() === 0)
  await screenshot(teacher.page, 'teacher-withdrawn-resource')
  await student.page.goto('/my-feedback')
  await studentRow.getByText(reply, { exact: true }).waitFor()
  check('feedback history remains visible while withdrawn', await studentRow.isVisible())
  await admin.page.locator('#publication-reason').fill(`浏览器验收完毕恢复-${tag}`)
  await changed(admin.page, `/api/admin/published-resources/${id}/restore`, () => panel.getByRole('button', { name: '确认恢复发布', exact: true }).click())
  await panel.getByRole('status').filter({ hasText: '资源已恢复发布' }).waitFor()
  check('publication timeline keeps both real operations', await panel.locator('.publication-events li').count() === 2)
  await panel.scrollIntoViewIfNeeded()
  await screenshot(admin.page, 'admin-publication-restored', false)
  const restored = await jsonOK(await api(student, 'GET', `/resources/${id}`))
  check('first publication time retained', restored.publishedAt === publishedAt)
  check('audit history unchanged', JSON.stringify((await jsonOK(await api(admin, 'GET', `/admin/resource-reviews/${id}`))).auditRecords) === JSON.stringify(originalAudits))
  const download = await api(student, 'GET', `/resources/${id}/download`)
  check('original file retained byte for byte', download.status() === 200 && (await download.body()).equals(pdf))
  await student.page.goto(`/resources/${id}`)
  await student.page.getByRole('heading', { name: title, exact: true }).waitFor()
  await student.page.getByRole('button', { name: '反馈问题', exact: true }).waitFor()
  check('restored resource visible again in browser', true)
  await screenshot(student.page, 'student-resource-restored')
  check('no browser page errors', errors.length === 0)
  console.log(`GOVERNANCE_BROWSER_CHECKS_PASSED=${checks} RESOURCE_ID=${id} TEST_TAG=${tag}`)
  console.log(`SCREENSHOTS=${output}`)
} finally {
  try {
    if (id && admin) {
      const publication = await jsonOK(await api(admin, 'GET', `/admin/published-resources/${id}/publication`))
      if (publication.withdrawnAt) await jsonOK(await api(admin, 'POST', `/admin/published-resources/${id}/restore`, { data: { reason: '本地浏览器验收收尾：恢复本轮自有夹具' } }))
    }
  } catch (error) { console.error(`Own fixture ${id} cleanup needs inspection: ${error.message}`); process.exitCode = 1 }
  await browser.close()
}

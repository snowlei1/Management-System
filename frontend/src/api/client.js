let csrfToken = null
let csrfHeader = 'X-CSRF-TOKEN'

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function ensureCsrf() {
  if (csrfToken) return
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  const body = await response.json()
  if (!response.ok || body.code !== 'OK') {
    throw new ApiError(response.status, body.code, body.message || '无法建立安全会话')
  }
  csrfToken = body.data.token
  csrfHeader = body.data.headerName
}

export function clearCsrf() {
  csrfToken = null
}

export async function api(path, { method = 'GET', body, responseType = 'json' } = {}) {
  const isMultipart = body instanceof FormData
  const headers = { Accept: 'application/json' }
  if (method !== 'GET' && method !== 'HEAD') {
    await ensureCsrf()
    headers[csrfHeader] = csrfToken
  }
  if (body !== undefined && !isMultipart) headers['Content-Type'] = 'application/json'
  const response = await fetch(`/api${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : isMultipart ? body : JSON.stringify(body),
  })
  if (response.ok && responseType === 'blob') return response.blob()
  const result = await response.json().catch(() => null)
  if (!response.ok || result?.code !== 'OK') {
    if (response.status === 401) {
      clearCsrf()
      window.dispatchEvent(new Event('auth-expired'))
    }
    const message = result?.message || '请求失败'
    if (!(path === '/auth/me' && response.status === 401)) window.dispatchEvent(new CustomEvent('app-toast', { detail: { type: 'error', message } }))
    throw new ApiError(response.status, result?.code || 'REQUEST_FAILED', message)
  }
  if (!['GET', 'HEAD'].includes(method) && !path.startsWith('/auth/')) window.dispatchEvent(new CustomEvent('app-toast', { detail: { type: 'success', message: '操作已完成' } }))
  return result.data
}

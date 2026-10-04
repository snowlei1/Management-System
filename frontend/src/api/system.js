export async function getSystemStatus() {
  const response = await fetch('/api/system/ping')
  const body = await response.json()
  if (!response.ok || body.code !== 'OK') {
    throw new Error(body.message || '连接检查失败')
  }
  return body.data
}

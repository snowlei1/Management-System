export const resourceStates = { DRAFT: '草稿', PENDING: '待审核', REJECTED: '审核驳回', APPROVED: '审核通过 / 已发布' }
export const editableResource = r => ['DRAFT', 'REJECTED'].includes(r?.status)
export const timeText = value => value?.replace('T', ' ').slice(0, 23) || '—'

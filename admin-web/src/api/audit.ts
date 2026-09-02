import http from './http'

export interface AuditLog {
  id: number
  operatorId: number | null
  operatorName: string | null
  action: string
  targetNo: string | null
  detail: string | null
  createdAt: string
}

export async function listAuditLogs(params: {
  operatorId?: number
  page: number
  size: number
}): Promise<{ list: AuditLog[]; total: number }> {
  const resp = await http.get('/audit/logs', { params })
  return resp.data.data
}

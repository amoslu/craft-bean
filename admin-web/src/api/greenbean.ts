import http from './http'
import type { PageResult, GreenBean, Supplier } from './archive'

export interface GreenLot {
  id: number; lotNo: string; greenBeanId: number; greenBeanName?: string; supplierId: number
  supplierName?: string; purchaseDate: string; totalKg: number; remainingKg: number
  costPrice: number; amount: number; status: string; remark?: string
}
export interface InventoryMovement {
  id: number; bizType: string; batchId: number; lotNo?: string; greenBeanName?: string
  changeKg: number; unitPrice?: number; amount?: number; movementDate: string; remark?: string
}
export interface PurchaseRequest {
  supplierId: number; greenBeanId: number; purchaseDate: string; lotNo?: string
  quantityKg: number; unitPrice: number; amount?: number; remark?: string
}
export async function purchase(data: PurchaseRequest): Promise<GreenLot> {
  const resp = await http.post('/greenbean/purchases', data); return resp.data.data
}
/** 作废批次：单据留档不物理删除，未领用部分由反向流水冲销。仅管理员。 */
export async function voidLot(id: number, reason?: string): Promise<GreenLot> {
  const resp = await http.post(`/greenbean/lots/${id}/void`, { reason }); return resp.data.data
}
export async function listLots(params: {
  keyword?: string; status?: string; supplierId?: number; greenBeanId?: number; page: number; size: number
}): Promise<PageResult<GreenLot>> {
  const resp = await http.get('/greenbean/inventory/lots', { params }); return resp.data.data
}
export async function listMovements(params: {
  greenBeanId?: number; lotId?: number; bizType?: string; page: number; size: number
}): Promise<PageResult<InventoryMovement>> {
  const resp = await http.get('/greenbean/inventory/movements', { params }); return resp.data.data
}
export type { GreenBean, Supplier }

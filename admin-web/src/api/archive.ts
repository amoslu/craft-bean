import http from './http'

export interface PageResult<T> { list: T[]; total: number }
export interface Supplier {
  id: number; name: string; contactPerson?: string; phone?: string; wechat?: string; address?: string
  mainCategory?: string; status: string; createdAt?: string; updatedAt?: string
}
export interface BrewMethod {
  id: number; name: string; status: string; createdAt?: string; updatedAt?: string
}
export interface GreenBean {
  id: number
  code?: string
  beanType?: 'SINGLE' | 'BLEND'
  name: string
  origin?: string
  region?: string
  process?: string
  variety?: string
  season?: string
  flavor?: string
  recipe?: string
  agtronColor?: number
  moisturePct?: number
  altitudeM?: number
  recommendedRoast?: string
  packageSpec?: string
  defaultSupplierId?: number
  status: string
  remark?: string
  createdAt?: string
  updatedAt?: string
  brewMethodIds?: number[]
  brewMethodNames?: string[]
}

export async function listSuppliers(params: { keyword?: string; status?: string; page: number; size: number }): Promise<PageResult<Supplier>> {
  const resp = await http.get('/archive/suppliers', { params })
  return resp.data.data
}
export async function createSupplier(data: Partial<Supplier>): Promise<Supplier> {
  const resp = await http.post('/archive/suppliers', data); return resp.data.data
}
export async function updateSupplier(id: number, data: Partial<Supplier>): Promise<Supplier> {
  const resp = await http.put(`/archive/suppliers/${id}`, data); return resp.data.data
}
export async function changeSupplierStatus(id: number, status: string): Promise<Supplier> {
  const resp = await http.post(`/archive/suppliers/${id}/status`, null, { params: { status } }); return resp.data.data
}
export async function deleteSupplier(id: number): Promise<void> {
  await http.delete(`/archive/suppliers/${id}`)
}

export async function listBrewMethods(params: { keyword?: string; status?: string; page: number; size: number }): Promise<PageResult<BrewMethod>> {
  const resp = await http.get('/archive/brew-methods', { params })
  return resp.data.data
}
export async function createBrewMethod(data: Partial<BrewMethod>): Promise<BrewMethod> {
  const resp = await http.post('/archive/brew-methods', data); return resp.data.data
}
export async function updateBrewMethod(id: number, data: Partial<BrewMethod>): Promise<BrewMethod> {
  const resp = await http.put(`/archive/brew-methods/${id}`, data); return resp.data.data
}
export async function changeBrewMethodStatus(id: number, status: string): Promise<BrewMethod> {
  const resp = await http.post(`/archive/brew-methods/${id}/status`, null, { params: { status } }); return resp.data.data
}
export async function deleteBrewMethod(id: number): Promise<void> {
  await http.delete(`/archive/brew-methods/${id}`)
}

export async function listGreenBeans(params: { keyword?: string; status?: string; page: number; size: number; includeMethods?: boolean }): Promise<PageResult<GreenBean>> {
  const resp = await http.get('/archive/green-beans', { params })
  return resp.data.data
}
export async function createGreenBean(data: Partial<GreenBean>): Promise<GreenBean> {
  const resp = await http.post('/archive/green-beans', data); return resp.data.data
}
export async function updateGreenBean(id: number, data: Partial<GreenBean>): Promise<GreenBean> {
  const resp = await http.put(`/archive/green-beans/${id}`, data); return resp.data.data
}
export async function deleteGreenBean(id: number): Promise<void> {
  await http.delete(`/archive/green-beans/${id}`)
}

import http from './http'

export interface User {
  id: number
  username: string
  name: string
  role: string
  status: string
  lastLoginAt: string | null
  createdAt: string | null
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export async function listUsers(params: {
  keyword?: string
  role?: string
  status?: string
  page: number
  size: number
}): Promise<PageResult<User>> {
  const resp = await http.get('/system/users', { params })
  return resp.data.data as PageResult<User>
}

export async function createUser(data: { username: string; name: string; password: string; role: string }): Promise<User> {
  const resp = await http.post('/system/users', data)
  return resp.data.data as User
}

export async function updateUser(id: number, data: { name: string; role: string; status: string }): Promise<User> {
  const resp = await http.put(`/system/users/${id}`, data)
  return resp.data.data as User
}

export async function resetPassword(id: number, newPassword: string): Promise<void> {
  await http.post(`/system/users/${id}/reset-password`, { newPassword })
}

export async function deleteUser(id: number): Promise<void> {
  await http.delete(`/system/users/${id}`)
}

import http from './http'

export interface CurrentUser {
  id: number
  username: string
  name: string
  role: string
}

export interface LoginResult {
  token: string
  user: CurrentUser
}

export async function loginApi(username: string, password: string): Promise<LoginResult> {
  const resp = await http.post('/auth/login', { username, password })
  return resp.data.data as LoginResult
}

export async function meApi(): Promise<CurrentUser> {
  const resp = await http.get('/auth/me')
  return resp.data.data as CurrentUser
}

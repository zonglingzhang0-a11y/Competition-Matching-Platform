import axios, { type AxiosError } from 'axios'

/** 后端统一响应体 */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 接口失败时抛出的错误，message 可以直接展示给用户 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

const TOKEN_KEY = 'tm_token'

export function loadToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function saveToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 无痕模式等情况下存不进去，登录状态只在当前页面有效
  }
}

let unauthorizedHandler: (() => void) | null = null

/** 登录失效（后端返回 401）时要做什么，在 main.ts 里设置 */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = loadToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res,
  (err: AxiosError<ApiResult<unknown>>) => {
    const status = err.response?.status ?? 0
    const message =
      err.response?.data?.message ?? (status ? `请求失败（${status}）` : '连不上服务器，请确认后端已经启动')
    if (status === 401) unauthorizedHandler?.()
    return Promise.reject(new ApiError(status, message))
  },
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiResult<T>>(url, { params })
  return res.data.data
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.post<ApiResult<T>>(url, body)
  return res.data.data
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.put<ApiResult<T>>(url, body)
  return res.data.data
}

export async function del<T>(url: string): Promise<T> {
  const res = await http.delete<ApiResult<T>>(url)
  return res.data.data
}

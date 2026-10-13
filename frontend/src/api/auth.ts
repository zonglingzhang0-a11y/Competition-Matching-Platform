import { get, post, postForm } from './http'
import type { LatestVerifyRequest, LoginResponse, Me, RegisterForm, ScreenshotResult, VerifyResult } from '@/types/api'

export const sendRegisterCode = (email: string) => post<void>('/auth/register-code', { email })

export const register = (form: RegisterForm) => post<LoginResponse>('/auth/register', form)

export const login = (email: string, password: string) => post<LoginResponse>('/auth/login', { email, password })

export const logout = () => post<void>('/auth/logout')

export const fetchMe = () => get<Me>('/users/me')

export const sendEduCode = (eduEmail: string) => post<void>('/verify/edu/code', { eduEmail })

export const confirmEdu = (eduEmail: string, code: string) =>
  post<VerifyResult>('/verify/edu/confirm', { eduEmail, code })

/** 最近一次认证申请，没提交过时为 null */
export const latestVerify = () => get<LatestVerifyRequest | null>('/verify/latest')

/** 截图认证：图片加上和图片上一致的姓名、学校、入学年份 */
export function submitScreenshot(file: File, realName: string, schoolName: string, enrollYear: number) {
  const form = new FormData()
  form.append('file', file)
  form.append('realName', realName)
  form.append('schoolName', schoolName)
  form.append('enrollYear', String(enrollYear))
  return postForm<ScreenshotResult>('/verify/screenshot', form)
}

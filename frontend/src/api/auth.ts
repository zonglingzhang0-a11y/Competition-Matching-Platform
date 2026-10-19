import { get, post, postForm, put } from './http'
import type { LatestVerifyRequest, LoginResponse, Me, RegisterForm, ScreenshotResult, VerifyResult } from '@/types/api'

export const sendRegisterCode = (email: string) => post<void>('/auth/register-code', { email })

export const register = (form: RegisterForm) => post<LoginResponse>('/auth/register', form)

export const login = (email: string, password: string) => post<LoginResponse>('/auth/login', { email, password })

export const logout = () => post<void>('/auth/logout')

/** 忘记密码：发验证码（邮箱没注册也返回成功） */
export const sendResetCode = (email: string) => post<void>('/auth/reset-code', { email })

/** 用验证码设新密码，之后要用新密码重新登录 */
export const resetPassword = (form: { email: string; code: string; password: string }) =>
  post<void>('/auth/reset-password', form)

export const fetchMe = () => get<Me>('/users/me')

/** 不在网站上时，要不要把没看的新消息汇总发到邮箱 */
export const setEmailNotify = (enabled: boolean) => put<void>('/users/me/email-notify', { enabled })

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

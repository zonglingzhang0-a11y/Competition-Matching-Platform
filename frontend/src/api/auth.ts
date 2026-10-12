import { get, post } from './http'
import type { LatestVerifyRequest, LoginResponse, Me, RegisterForm, VerifyResult } from '@/types/api'

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

// 和后端接口对应的类型

export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'

/** NONE 未认证 / PENDING 邮箱已验证、学校待确认 / VERIFIED 已认证 */
export type VerifyStatus = 'NONE' | 'PENDING' | 'VERIFIED'

export interface Me {
  id: number
  email: string
  nickname: string
  role: UserRole
  verifyStatus: VerifyStatus
  verifiedUntil: string | null
  school: { id: number; name: string } | null
}

export interface LoginResponse {
  token: string
  me: Me
}

export interface RegisterForm {
  email: string
  code: string
  password: string
  nickname: string
}

export interface VerifyResult {
  verifyStatus: VerifyStatus
  schoolName: string | null
  verifiedUntil: string | null
  message: string
}

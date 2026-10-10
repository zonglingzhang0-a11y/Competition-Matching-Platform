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

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

// ---------- 比赛库 ----------

export type CompetitionLevel = 'NATIONAL' | 'PROVINCIAL' | 'SCHOOL'

/** 报名阶段：时间待定 / 未开始 / 报名中 / 即将截止 / 已截止 */
export type EditionPhase = 'UNKNOWN' | 'UPCOMING' | 'OPEN' | 'CLOSING_SOON' | 'CLOSED'

export type NodeType = 'REGISTRATION' | 'CONTEST' | 'SUBMISSION' | 'RESULT' | 'OTHER'

/** FIXED 确定 / RANGE 结束在一个区间里 / OPEN 还没有结束时间 */
export type EndMode = 'FIXED' | 'RANGE' | 'OPEN'

export interface EditionBrief {
  id: number
  title: string
  teamMin: number
  teamMax: number
  registrationEnd: string | null
  phase: EditionPhase
  followed: boolean
}

export interface CompetitionSummary {
  id: number
  name: string
  shortName: string | null
  category: string
  level: CompetitionLevel
  inCaheList: boolean
  currentEdition: EditionBrief | null
}

export interface CompetitionNode {
  id: number
  title: string
  nodeType: NodeType
  startAt: string
  endAt: string | null
  allDay: boolean
  startLatestAt: string | null
  endMode: EndMode
  endLatestAt: string | null
  busy: boolean
  note: string | null
}

export interface EditionDetail {
  id: number
  year: number
  title: string
  teamMin: number
  teamMax: number
  sameSchoolRequired: boolean
  advisorRequired: boolean
  englishRequired: boolean
  registrationStart: string | null
  registrationEnd: string | null
  noticeUrl: string | null
  phase: EditionPhase
  followed: boolean
  followers: number
  nodes: CompetitionNode[]
}

export interface CompetitionDetail {
  id: number
  name: string
  shortName: string | null
  organizer: string | null
  category: string
  level: CompetitionLevel
  inCaheList: boolean
  officialUrl: string | null
  description: string | null
  roles: string[]
  editions: EditionDetail[]
}

export interface CompetitionQuery {
  keyword?: string
  category?: string
  level?: CompetitionLevel
  openOnly?: boolean
  followedOnly?: boolean
  page: number
  size: number
}

export interface Option {
  id: number
  name: string
}

// ---------- 个人档案 ----------

export interface SkillOption {
  id: number
  name: string
  category: string
}

export interface SkillLevel {
  skillId: number
  name: string
  category: string
  /** 1 入门 / 2 熟练 / 3 精通 */
  level: number
}

export interface Experience {
  id: number
  competitionId: number | null
  title: string
  year: number
  award: string | null
  proofUrl: string | null
  source: 'MANUAL' | 'PLATFORM'
  verified: boolean
}

export interface ProfileForm {
  nickname: string
  realName: string
  gender: string
  major: string
  enrollYear: number | null
  degree: string
  bio: string
  contactWechat: string
  contactQq: string
}

export interface Profile {
  email: string
  nickname: string
  verifyStatus: VerifyStatus
  school: { id: number; name: string } | null
  realName: string | null
  gender: string | null
  major: string | null
  enrollYear: number | null
  degree: string | null
  bio: string | null
  contactWechat: string | null
  contactQq: string | null
  skills: SkillLevel[]
  experiences: Experience[]
}

export interface ExperienceForm {
  competitionId: number | null
  title: string
  year: number
  award: string
  proofUrl: string
}

export interface VerifyResult {
  verifyStatus: VerifyStatus
  schoolName: string | null
  verifiedUntil: string | null
  message: string
}

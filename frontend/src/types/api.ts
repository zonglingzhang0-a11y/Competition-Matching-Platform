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

// ---------- 队伍 ----------

export interface UserBrief {
  id: number
  nickname: string
  schoolId: number | null
  schoolName: string | null
  major: string | null
  enrollYear: number | null
  skills: SkillLevel[]
}

export interface Contact {
  realName: string | null
  wechat: string | null
  qq: string | null
}

export type TeamStatus = 'RECRUITING' | 'FULL' | 'CLOSED' | 'FINISHED' | 'DISBANDED'
export type MeetingMode = 'OFFLINE' | 'ONLINE' | 'MIXED'
export type NeedStatus = 'OPEN' | 'FILLED' | 'CANCELLED'
export type AppDirection = 'APPLY' | 'INVITE'
export type AppStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN' | 'EXPIRED'

export interface EditionRef {
  editionId: number
  competitionId: number
  competitionName: string
  editionTitle: string
  phase: EditionPhase
  teamMin: number
  teamMax: number
  sameSchoolRequired: boolean
}

export interface NeedView {
  id: number
  roleName: string
  headcount: number
  filled: number
  note: string | null
  status: NeedStatus
  skills: { skillId: number; name: string; minLevel: number }[]
}

export interface MemberView {
  user: UserBrief
  roleName: string | null
  captain: boolean
  joinedAt: string
  /** 只有队友能看到 */
  contact: Contact | null
}

export interface AppBrief {
  id: number
  direction: AppDirection
  createdAt: string
}

export interface MyRelation {
  member: boolean
  captain: boolean
  pendingApplication: AppBrief | null
  pendingInvitation: AppBrief | null
  /** 为空表示可以申请 */
  applyBlockedReason: string | null
}

export interface TeamDetail {
  id: number
  name: string
  intro: string | null
  status: TeamStatus
  maxMembers: number
  meetingMode: MeetingMode
  memberCount: number
  edition: EditionRef
  captainId: number
  members: MemberView[]
  needs: NeedView[]
  me: MyRelation
  createdAt: string
}

export interface TeamSummary {
  id: number
  name: string
  intro: string | null
  status: TeamStatus
  maxMembers: number
  memberCount: number
  captain: UserBrief
  openNeeds: NeedView[]
  edition: EditionRef
}

export interface TeamForm {
  name: string
  intro: string
  maxMembers: number
  meetingMode: MeetingMode
}

export interface NeedForm {
  roleName: string
  headcount: number
  note: string
  skills: { skillId: number; minLevel: number }[]
}

export interface ApplicationView {
  id: number
  direction: AppDirection
  status: AppStatus
  message: string | null
  needRoleName: string | null
  createdAt: string
  handledAt: string | null
  team: { id: number; name: string; competitionName: string; editionTitle: string }
  user: UserBrief
}

export interface MyApplications {
  /** 我发出的申请 */
  sent: ApplicationView[]
  /** 我收到的邀请 */
  received: ApplicationView[]
}

export interface Seeker {
  user: UserBrief
  /** 这支队已经邀请过、对方还没回复 */
  invited: boolean
}

// ---------- 问卷 ----------

export type QuestionType = 'SINGLE' | 'MULTI' | 'RANKING' | 'SLIDER' | 'SLOT_GRID' | 'TEXT' | 'PLEDGE'

export interface SurveyOption {
  value: string
  label: string
  group: string | null
}

export interface SurveyQuestion {
  code: string
  part: 'GENERAL' | 'COMPETITION'
  dimension: string | null
  type: QuestionType
  title: string
  note: string | null
  options: SurveyOption[] | null
  settings: Record<string, unknown>
  required: boolean
  inQuick: boolean
}

export interface Survey {
  version: string
  questions: SurveyQuestion[]
}

/** 题目代码 → 答案 */
export type Answers = Record<string, unknown>

export interface MyAnswers {
  answers: Answers
  quick: boolean
  submittedAt: string
  pledgedAt: string | null
}

export interface EditionAnswers {
  editionId: number
  competitionName: string
  editionTitle: string
  roles: string[]
  mine: MyAnswers | null
}

// ---------- 匹配推荐 ----------

export interface MatchResult {
  /** 0–100 */
  score: number
  k: number
  /** 问卷数据完整度 0–1 */
  completeness: number
  dimensions: { key: string; name: string; score: number | null }[]
  reasons: string[]
  talkAbout: string | null
  warnings: string[]
  veto: string | null
}

export interface RecommendedTeam {
  team: TeamSummary
  match: MatchResult
}

export interface TeamRecommendations {
  editionId: number
  /** 已经在这一届的某支队里 */
  currentTeamId: number | null
  generalSurveyDone: boolean
  editionSurveyDone: boolean
  items: RecommendedTeam[]
  note: string | null
}

export interface RecommendedUser {
  user: UserBrief
  invited: boolean
  match: MatchResult
}

export interface HomeRecommendation {
  edition: EditionRef
  teams: RecommendedTeam[]
}

// ---------- 日历 ----------

/** OFFICIAL 比赛官方节点 / TEAM 队伍里程碑 / PERSONAL 个人日程 */
export type CalendarLayer = 'OFFICIAL' | 'TEAM' | 'PERSONAL'

export interface CalendarEvent {
  id: string
  layer: CalendarLayer
  /** 图层细分，比如 edition-1、team-2、personal */
  layerKey: string
  layerName: string
  title: string
  start: string
  end: string | null
  allDay: boolean
  /** 时间说不准 */
  fuzzy: boolean
  /** 参与撞期检测 */
  busy: boolean
  done: boolean | null
  conflictWith: string | null
  refType: 'COMPETITION' | 'TEAM' | 'EVENT' | null
  refId: number | null
  /** 个人重复日程这一次的日期 */
  occurrenceDate: string | null
}

export interface CalendarConflict {
  first: string
  second: string
  start: string
}

export interface CalendarData {
  events: CalendarEvent[]
  conflicts: CalendarConflict[]
}

export type RepeatType = 'NONE' | 'DAILY' | 'WEEKDAYS' | 'WEEKLY'

export interface EventForm {
  title: string
  notes: string
  color: string | null
  startAt: string
  endAt: string | null
  allDay: boolean
  repeatType: RepeatType
  /** 1 = 周一 … 7 = 周日 */
  repeatWeekdays: number[]
  repeatUntil: string | null
  busy: boolean
}

export interface EventView extends EventForm {
  id: number
}

export interface MilestoneForm {
  title: string
  note: string
  startAt: string
  endAt: string | null
  allDay: boolean
  endMode: EndMode
  endLatestAt: string | null
}

export interface MilestoneView {
  id: number
  title: string
  note: string | null
  startAt: string
  endAt: string | null
  allDay: boolean
  endMode: EndMode
  endLatestAt: string | null
  doneAt: string | null
}

export interface TeamTimeline {
  official: CompetitionNode[]
  /** 只有队员能看到 */
  milestones: MilestoneView[]
  member: boolean
  canEdit: boolean
}

// ---------- 站内信 ----------

export interface Notification {
  id: number
  type: string
  title: string
  content: string | null
  refType: string | null
  refId: number | null
  readAt: string | null
  createdAt: string
}

export interface VerifyResult {
  verifyStatus: VerifyStatus
  schoolName: string | null
  verifiedUntil: string | null
  message: string
}

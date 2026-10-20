// 和后端接口对应的类型

export type UserRole = 'STUDENT' | 'ADMIN'

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
  /** 不在网站上时，把没看的新消息汇总发到邮箱 */
  emailNotify: boolean
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
  /** 本校同学标注、确认够了的加分认定级别，比如"A 类" */
  recognition: string | null
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
  /** 关联了比赛库的，本校同学确认好的加分认定级别 */
  recognition: string | null
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
  /** 个人资料页上的参赛履历和组队记录给谁看 */
  historyVisibility: HistoryVisibility
}

export interface ExperienceForm {
  competitionId: number | null
  title: string
  year: number
  award: string
  proofUrl: string
}

// ---------- 个人资料页 ----------

/**
 * 看资料页的人和资料页主人的关系：SELF 自己 / TEAMMATE 队友 /
 * RELATED 对方主动找过来（申请了我所在的队，或者对方的队邀请了我；或者是管理员）/ MEMBER 其他已认证同学 / GUEST 还没认证
 */
export type PageAccessLevel = 'SELF' | 'TEAMMATE' | 'RELATED' | 'MEMBER' | 'GUEST'

/** 参赛履历和组队记录给谁看：PUBLIC 所有已认证同学 / RELATED 只给队友和主动联系过的人 */
export type HistoryVisibility = 'PUBLIC' | 'RELATED'

/** IN_TEAM 还在队里 / FINISHED 比赛已结束 / LEFT 已离队 / DISBANDED 队伍已解散 */
export type TeamRecordState = 'IN_TEAM' | 'FINISHED' | 'LEFT' | 'DISBANDED'

export interface PageExperience {
  id: number
  title: string
  year: number
  award: string | null
  /** 证明链接（证书照片上一般有真实姓名）只给队友、主动联系过的人和管理员；其他人只看到 hasProof */
  proofUrl: string | null
  hasProof: boolean
  source: 'MANUAL' | 'PLATFORM'
  verified: boolean
}

/** 一条平台内的组队记录；days 是在队天数，比赛结束了又没记下结束时间的为空 */
export interface PageTeamRecord {
  teamId: number
  teamName: string
  competitionId: number
  competitionName: string
  editionTitle: string
  roleName: string | null
  captain: boolean
  state: TeamRecordState
  /** 比赛结束时队长录入的获奖 */
  award: string | null
  joinedAt: string
  days: number | null
}

export interface UserPage {
  id: number
  nickname: string
  schoolName: string | null
  verified: boolean
  major: string | null
  enrollYear: number | null
  degree: string | null
  bio: string | null
  joinedAt: string | null
  skills: SkillLevel[]
  /** 互评信用；score 为空表示新用户 */
  credit: { score: number | null; ratedRounds: number }
  /** 问卷开放题：我能给队伍带来的三点 */
  selfIntro: string | null
  /** 问卷开放题：希望队友包容我的一个短板 */
  weakness: string | null
  /** 看不到履历时为空 */
  stats: { teams: number; finished: number; captainTimes: number; awards: number; memberDays: number } | null
  /** 最高荣誉：优先是平台记录和已核验的 */
  highlight: PageExperience | null
  teams: PageTeamRecord[]
  experiences: PageExperience[]
  /** 共同点；看自己、或者自己还没认证时为空 */
  mutual: {
    sameSchool: boolean
    editions: { competitionId: number; competitionName: string; editionTitle: string }[]
    teams: { teamId: number; teamName: string; competitionName: string; current: boolean }[]
  } | null
  /** 只有队友能看到 */
  contact: Contact | null
  access: {
    level: PageAccessLevel
    historyVisible: boolean
    contactVisible: boolean
    /** 有内容被隐藏时的说明 */
    notice: string | null
    /** 本人的设置，只有看自己时才有 */
    historyVisibility: HistoryVisibility | null
    /** 自己在预览"别人看到的我" */
    preview: boolean
  }
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
  /** 互评得出的信用分 0–100，新用户为空 */
  credit: number | null
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
  /** 比赛结束时录入的奖项 */
  award: string | null
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

/** 批量撮合（只给建议）：一种建议的组合 */
export interface SuggestedGroup {
  /** 除了我以外的人 */
  members: UserBrief[]
  /** 分数是组合里每个人和其余人打分的平均；推荐理由是我和其余人的 */
  match: MatchResult
}

export interface GroupSuggestions {
  editionId: number
  currentTeamId: number | null
  /** 建议的组合是几个人（含我） */
  groupSize: number
  /** 没有建议时说明为什么 */
  note: string | null
  groups: SuggestedGroup[]
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
  /** 截止类：报名截止、作品提交、队伍里程碑，画成红色菱形 */
  deadline: boolean
  /** 官方节点：这一届已经组队参加（光关注的为 false） */
  joined: boolean
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
  /** 时间说不准：最晚哪天开始；结束是确定的、一个范围（endAt 到 endLatestAt），还是做完为止。说不准的不能重复、不参与撞期检测 */
  startLatestAt: string | null
  endMode: EndMode
  endLatestAt: string | null
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
  /** 这个节点下的分工待办 */
  tasks: TaskView[]
}

export interface TeamTimeline {
  official: CompetitionNode[]
  /** 只有队员能看到 */
  milestones: MilestoneView[]
  member: boolean
  canEdit: boolean
  /** 现在的队员（队长在前），分任务时选人用；不是队员时为空 */
  members: { id: number; nickname: string }[]
}

export interface TaskView {
  id: number
  title: string
  /** 为空表示还没人认领 */
  assigneeId: number | null
  assigneeName: string | null
  /** 负责人已经离队：算没人负责，谁都可以认领 */
  assigneeLeft: boolean
  doneAt: string | null
}

export interface TaskForm {
  title: string
  assigneeId: number | null
}

/** 分给我、还没做完的任务 */
export interface MyTask {
  id: number
  title: string
  teamId: number
  teamName: string
  milestoneId: number
  milestoneTitle: string
  startAt: string
  endAt: string | null
  allDay: boolean
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

export interface LatestVerifyRequest {
  status: ReviewStatus
  method: 'EDU_EMAIL' | 'SCREENSHOT'
  /** 截图认证时为空 */
  eduEmail: string | null
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
}

// ---------- 管理端 ----------

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type UserStatus = 'ACTIVE' | 'BANNED'
export type EditionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type MatchPreset = 'DEFAULT' | 'SHORT_INTENSIVE' | 'LONG_TERM'

export interface ReviewItem {
  id: number
  method: 'EDU_EMAIL' | 'SCREENSHOT'
  status: ReviewStatus
  eduEmail: string | null
  /** 可以收录的后缀，从长到短 */
  domains: string[]
  user: {
    id: number
    nickname: string | null
    email: string | null
    realName: string | null
    major: string | null
    enrollYear: number | null
  }
  school: Option | null
  reviewer: string | null
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
  /** 截图认证才有 */
  screenshot: ScreenshotInfo | null
}

/** 截图里每一项有没有认出来 */
export interface ScreenshotMatched {
  realName: boolean
  schoolName: boolean
  enrollYear: boolean
}

/** HIGH 三项都认出来了 / LOW 有对不上的 / null 没能识别 */
export type OcrConfidence = 'HIGH' | 'LOW' | null

export interface ScreenshotInfo {
  realName: string | null
  schoolName: string | null
  enrollYear: number | null
  confidence: OcrConfidence
  matched: ScreenshotMatched | null
  lines: string[]
  suggestedSchool: Option | null
  hasImage: boolean
}

export interface ScreenshotResult {
  verifyStatus: VerifyStatus
  confidence: OcrConfidence
  matched: ScreenshotMatched | null
  message: string
}

export interface ApproveForm {
  schoolId?: number
  newSchoolName?: string
  province?: string
  city?: string
  /** 教育邮箱认证要选；截图认证不用 */
  domain?: string
  note?: string
}

export interface ApproveResult {
  schoolName: string
  alsoApproved: number
}

export interface SchoolRow {
  id: number
  name: string
  province: string | null
  city: string | null
  domains: { id: number; domain: string }[]
  verifiedUsers: number
}

export interface SchoolForm {
  name: string
  province?: string
  city?: string
}

export interface AdminUserRow {
  id: number
  email: string
  nickname: string
  role: UserRole
  status: UserStatus
  verifyStatus: VerifyStatus
  schoolName: string | null
  createdAt: string
  lastLoginAt: string | null
}

export interface EditionForm {
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
  status: EditionStatus
}

export interface AdminEditionRow extends EditionForm {
  id: number
  phase: EditionPhase
  teams: number
  nodes: number
}

export interface CompetitionForm {
  name: string
  shortName: string | null
  organizer: string | null
  category: string
  level: CompetitionLevel
  inCaheList: boolean
  officialUrl: string | null
  description: string | null
  roleTemplateId: number | null
  matchPreset: MatchPreset
}

export interface AdminCompetitionRow extends CompetitionForm {
  id: number
  editions: AdminEditionRow[]
}

export interface NodeForm {
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

export interface RoleTemplate {
  id: number
  name: string
  roles: string[]
}

export interface Overview {
  users: number
  verifiedUsers: number
  newUsers7d: number
  pendingVerify: number
  surveyDone: number
  teams: Partial<Record<TeamStatus, number>>
  openSlots: number
  applications: Partial<Record<AppStatus, number>>
  competitions: number
  openEditions: number
}

export interface EditionStat {
  editionId: number
  competitionName: string
  editionTitle: string
  phase: EditionPhase
  registrationEnd: string | null
  recruitingTeams: number
  formedTeams: number
  openSlots: number
  seekers: number
  followers: number
}

export interface SkillGap {
  skillId: number
  name: string
  category: string
  demand: number
  supply: number
  /** [入门, 熟练, 精通] */
  supplyByLevel: [number, number, number]
  gap: number
}

export interface PresetConfig {
  preset: MatchPreset
  config: string | null
  updatedAt: string | null
  updatedBy: string | null
}

export interface ConfigQuestion {
  code: string
  part: 'GENERAL' | 'COMPETITION'
  type: QuestionType
  title: string
  options: SurveyOption[] | null
}

export interface ConfigPage {
  versionName: string
  questions: ConfigQuestion[]
  presets: PresetConfig[]
  scorers: string[]
}

// ---------- 互评、信用、举报 ----------

export type RoundStatus = 'OPEN' | 'PUBLISHED'

export interface RoundSummary {
  id: number
  teamId: number
  teamName: string | null
  competitionName: string | null
  editionTitle: string | null
  status: RoundStatus
  closesAt: string
  publishedAt: string | null
  /** 我是不是已经给所有队友打完分 */
  submitted: boolean
  raters: number
  participants: number
}

export interface ReviewTarget {
  user: UserBrief
  self: boolean
  answers: number[] | null
}

export interface RoundForm {
  round: RoundSummary
  items: string[]
  frequencies: string[]
  targets: ReviewTarget[]
  editable: boolean
}

export interface SubmitResult {
  published: boolean
  raters: number
  participants: number
}

export interface CreditView {
  score: number | null
  ratedRounds: number
  factor: number
  suspendedUntil: string | null
  itemsVisible: boolean
  itemNames: string[]
  items: number[] | null
  selfItems: number[] | null
  raters: number
  rounds: RoundSummary[]
}

export type ReportTarget = 'USER' | 'TEAM'
export type ReportReason = 'PLAGIARISM' | 'FORGERY' | 'ABUSE' | 'SPAM' | 'OTHER'
export type ReportStatus = 'PENDING' | 'CONFIRMED' | 'DISMISSED'
export type ReportAction = 'NONE' | 'SUSPEND_30' | 'BLOCK' | 'BAN' | 'CLOSE_TEAM'

export interface MyReport {
  id: number
  targetType: ReportTarget
  targetId: number
  targetName: string
  reason: ReportReason
  detail: string | null
  status: ReportStatus
  resultNote: string | null
  createdAt: string
  handledAt: string | null
}

export interface ReportRow {
  id: number
  reporter: string
  targetType: ReportTarget
  targetId: number
  targetName: string
  /** 用户的邮箱、队伍的队长 */
  targetSub: string | null
  reason: ReportReason
  detail: string | null
  status: ReportStatus
  handler: string | null
  resultNote: string | null
  createdAt: string
  handledAt: string | null
  /** 针对同一对象的举报一共几条 */
  sameTarget: number
}

export interface SuspendedRow {
  userId: number
  nickname: string
  email: string | null
  score: number | null
  ratedRounds: number
  suspendedUntil: string
  permanent: boolean
}

export interface FlaggedRow {
  reviewId: number
  roundId: number
  teamName: string | null
  rater: string
  ratee: string
  answers: number[]
  submittedAt: string
}

// ---------- 通知抓取（管理端） ----------

/** NEW 没抽出字段 / EXTRACTED 抽出了字段，这两种等确认；APPROVED 已采用 / IGNORED 已忽略 */
export type CrawlStatus = 'NEW' | 'EXTRACTED' | 'APPROVED' | 'IGNORED'

/** 从通知里抽出的字段，没找到的为 null */
export interface Extracted {
  year: number | null
  editionTitle: string | null
  registrationStart: string | null
  registrationEnd: string | null
  contestStart: string | null
  contestEnd: string | null
  /** 比赛时间只写了日期 */
  contestAllDay: boolean
  teamMin: number | null
  teamMax: number | null
  sameSchoolRequired: boolean | null
  advisorRequired: boolean | null
  /** 字段名（registration / contest / teamSize / sameSchool / advisor）→ 抽取依据的原句 */
  evidence: Record<string, string>
  /** rule：规则抽取 / llm：大模型抽取 */
  extractor: string
}

export interface CrawlRecordRow {
  id: number
  sourceId: number | null
  sourceName: string | null
  competitionId: number | null
  competitionName: string | null
  title: string | null
  sourceUrl: string
  status: CrawlStatus
  /** 自动忽略的原因 */
  note: string | null
  extracted: Extracted | null
  fetchedAt: string
  editionId: number | null
  editionTitle: string | null
  reviewerName: string | null
  reviewedAt: string | null
}

/** 采用抓取通知时可选的届次（届次表单的全部字段） */
export interface CrawlEdition extends EditionForm {
  id: number
}

export interface CrawlRecordDetail {
  record: CrawlRecordRow
  rawText: string | null
  /** 这个比赛已有的届次，可以选择更新其中一届 */
  editions: CrawlEdition[]
}

export interface CrawlApproveForm {
  /** null：新建一届 */
  editionId: number | null
  edition: EditionForm
  /** 这一届全部的时间节点（整体替换） */
  nodes: NodeForm[]
}

export interface CrawlSourceForm {
  competitionId: number | null
  name: string
  listUrl: string
  linkSelector: string
  titleKeywords: string | null
  excludeKeywords: string | null
  contentSelector: string | null
  allowLlm: boolean
  enabled: boolean
  note: string | null
}

export interface CrawlSourceRow extends CrawlSourceForm {
  id: number
  competitionId: number
  competitionName: string | null
  lastRunAt: string | null
  lastResult: string | null
  /** 等确认的通知数 */
  pending: number
  total: number
}

export interface PreviewLink {
  title: string
  url: string
  picked: boolean
  reason: string
}

export interface PreviewResult {
  linkCount: number
  links: PreviewLink[]
}

export interface CrawlRunStatus {
  running: boolean
  startedAt: string | null
  finishedAt: string | null
  summary: string | null
}

// ---------- 我的主页 ----------

/** 一个快到的节点。label 是比赛简称或队伍名；progress 是本阶段已经过了百分之几 */
export interface Upcoming {
  title: string
  label: string
  at: string
  daysLeft: number
  deadline: boolean
  team: boolean
  progress: number | null
  refType: 'COMPETITION' | 'TEAM'
  refId: number | null
}

export interface SemesterMark {
  title: string
  start: string
  /** 为空表示一个时间点 */
  end: string | null
  deadline: boolean
  team: boolean
  fuzzy: boolean
}

/** 学期赛程带的一行：一个比赛，连同自己队伍在这个比赛下的里程碑 */
export interface SemesterLane {
  editionId: number
  competitionId: number | null
  label: string
  joined: boolean
  marks: SemesterMark[]
}

/** 一周的负荷：空闲小时（没填问卷空闲时段为 null）、截止个数、要不要提醒 */
export interface WeekLoad {
  start: string
  freeHours: number | null
  deadlines: number
  warn: boolean
}

export interface Semester {
  start: string
  end: string
  lanes: SemesterLane[]
  weeks: WeekLoad[]
}

export interface HomeStage {
  title: string
  at: string
  state: 'DONE' | 'CURRENT' | 'TODO'
}

export interface MyCompetition {
  teamId: number
  teamName: string
  editionId: number
  competitionId: number
  competitionName: string
  level: CompetitionLevel | null
  editionTitle: string
  members: string[]
  memberCount: number
  maxMembers: number
  openNeeds: number
  captain: boolean
  officialUrl: string | null
  noticeUrl: string | null
  stages: HomeStage[]
  nextDeadline: Upcoming | null
}

export interface Suggestion {
  editionId: number
  competitionId: number
  competitionName: string
  level: CompetitionLevel | null
  editionTitle: string
  registrationEnd: string | null
  daysLeft: number | null
  /** 个人赛，不用组队 */
  solo: boolean
  recruitingTeams: number
  followed: boolean
  officialUrl: string | null
  noticeUrl: string | null
  /** 本校同学确认好的加分认定级别 */
  recognition: string | null
}

/** INVITE 我收到的邀请 / APPLY 有人申请加入我当队长的队；consequence 是处理后的影响 */
export interface HomePending {
  kind: 'INVITE' | 'APPLY'
  applicationId: number
  teamId: number
  teamName: string
  competitionName: string
  who: string | null
  /** 上面这个人的 id，点名字打开他的个人资料页 */
  whoId: number | null
  roleName: string | null
  message: string | null
  createdAt: string
  consequence: string | null
}

export interface HomeTeammate {
  teamId: number
  teamName: string
  userId: number
  nickname: string
  major: string | null
  enrollYear: number | null
  score: number
  reason: string | null
  invited: boolean
}

export interface HomeData {
  today: string
  next: Upcoming | null
  after: Upcoming[]
  /** 本周还能投入几小时；没填问卷空闲时段为 null */
  weekFreeHours: number | null
  weekDeadlines: number
  semester: Semester
  mine: MyCompetition[]
  suggestions: Suggestion[]
  pending: HomePending[]
  teammates: HomeTeammate[]
}

// ---------- 各校加分认定 ----------

export type RecognitionStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface RecognitionEntry {
  id: number
  gradeLabel: string
  note: string | null
  sourceUrl: string | null
  /** 确认的人数，不算提交人 */
  confirmCount: number
  status: RecognitionStatus
  mine: boolean
  confirmedByMe: boolean
  createdAt: string
}

export interface RecognitionMySchool {
  schoolId: number
  schoolName: string
  /** 认证有效才能标注和确认 */
  canContribute: boolean
  /** 现在对外显示的是哪一条 */
  shownId: number | null
  hasMine: boolean
  entries: RecognitionEntry[]
}

export interface RecognitionOtherSchool {
  schoolId: number
  schoolName: string
  gradeLabel: string
  note: string | null
  sourceUrl: string | null
  confirmCount: number
}

export interface RecognitionBoard {
  /** 没认证（没有学校）时为空 */
  mySchool: RecognitionMySchool | null
  others: RecognitionOtherSchool[]
  confirmsNeeded: number
}

export interface RecognitionForm {
  gradeLabel: string
  note: string
  sourceUrl: string
}

export interface RecognitionAdminRow {
  id: number
  schoolId: number
  schoolName: string | null
  competitionId: number
  competitionName: string
  gradeLabel: string
  note: string | null
  sourceUrl: string | null
  confirmCount: number
  status: RecognitionStatus
  submitterId: number
  submitterName: string | null
  createdAt: string
  /** 同校同比赛下没被驳回的说法有几种，大于 1 就是有分歧 */
  variants: number
}

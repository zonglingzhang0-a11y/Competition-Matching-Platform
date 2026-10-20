// 枚举值对应的中文和标签颜色

import type {
  AppStatus,
  CompetitionLevel,
  CrawlStatus,
  EditionPhase,
  EditionStatus,
  HistoryVisibility,
  MatchPreset,
  MeetingMode,
  NodeType,
  ReportAction,
  ReportReason,
  RecognitionStatus,
  ReportStatus,
  ReviewStatus,
  TeamRecordState,
  TeamStatus,
  UserRole,
  UserStatus,
  VerifyStatus,
} from '@/types/api'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export const CATEGORIES = ['数学', '计算机', '电子', '创新创业', '设计', '外语', '其他']

export const LEVEL_LABEL: Record<CompetitionLevel, string> = {
  NATIONAL: '国家级',
  PROVINCIAL: '省级',
  SCHOOL: '校级',
}

export const PHASE: Record<EditionPhase, { label: string; type: TagType }> = {
  UNKNOWN: { label: '报名时间待定', type: 'info' },
  UPCOMING: { label: '未开始报名', type: 'info' },
  OPEN: { label: '报名中', type: 'success' },
  CLOSING_SOON: { label: '即将截止', type: 'warning' },
  CLOSED: { label: '报名已截止', type: 'info' },
}

export const NODE_TYPE_LABEL: Record<NodeType, string> = {
  REGISTRATION: '报名',
  CONTEST: '比赛',
  SUBMISSION: '提交',
  RESULT: '结果',
  OTHER: '其他',
}

export const SKILL_LEVELS = [
  { value: 1, label: '入门' },
  { value: 2, label: '熟练' },
  { value: 3, label: '精通' },
]

export const GENDERS = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'OTHER', label: '不透露' },
]

export const DEGREES = [
  { value: 'JUNIOR_COLLEGE', label: '专科' },
  { value: 'UNDERGRAD', label: '本科' },
  { value: 'MASTER', label: '硕士' },
  { value: 'PHD', label: '博士' },
]

export const TEAM_STATUS: Record<TeamStatus, { label: string; type: TagType }> = {
  RECRUITING: { label: '招募中', type: 'success' },
  FULL: { label: '已满员', type: 'info' },
  CLOSED: { label: '停止招募', type: 'info' },
  FINISHED: { label: '比赛已结束', type: 'info' },
  DISBANDED: { label: '已解散', type: 'danger' },
}

export const MEETING_MODES: { value: MeetingMode; label: string }[] = [
  { value: 'OFFLINE', label: '线下为主' },
  { value: 'ONLINE', label: '线上为主' },
  { value: 'MIXED', label: '线上线下都行' },
]

export const MEETING_MODE_LABEL: Record<MeetingMode, string> = {
  OFFLINE: '线下为主',
  ONLINE: '线上为主',
  MIXED: '线上线下都行',
}

export const APP_STATUS: Record<AppStatus, { label: string; type: TagType }> = {
  PENDING: { label: '待处理', type: 'warning' },
  ACCEPTED: { label: '已加入', type: 'success' },
  REJECTED: { label: '被婉拒', type: 'info' },
  WITHDRAWN: { label: '已撤回', type: 'info' },
  EXPIRED: { label: '已失效', type: 'info' },
}

export const SKILL_LEVEL_LABEL: Record<number, string> = { 1: '入门', 2: '熟练', 3: '精通' }

export const AWARD_SUGGESTIONS = [
  '国家级一等奖',
  '国家级二等奖',
  '国家级三等奖',
  '省级一等奖',
  '省级二等奖',
  '省级三等奖',
  '校级奖项',
  '成功参赛',
]

// ---------- 管理端 ----------

export const EDITION_STATUS: Record<EditionStatus, { label: string; type: TagType }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
}

export const CRAWL_STATUS: Record<CrawlStatus, { label: string; type: TagType }> = {
  NEW: { label: '没抽出字段', type: 'info' },
  EXTRACTED: { label: '待确认', type: 'warning' },
  APPROVED: { label: '已采用', type: 'success' },
  IGNORED: { label: '已忽略', type: 'info' },
}

export const REVIEW_STATUS: Record<ReviewStatus, { label: string; type: TagType }> = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '未通过', type: 'danger' },
}

export const PRESET_LABEL: Record<MatchPreset, string> = {
  DEFAULT: '默认',
  SHORT_INTENSIVE: '短期集中型',
  LONG_TERM: '长周期型',
}

export const ROLE_LABEL: Record<UserRole, string> = {
  STUDENT: '学生',
  ADMIN: '管理员',
}

export const USER_STATUS: Record<UserStatus, { label: string; type: TagType }> = {
  ACTIVE: { label: '正常', type: 'success' },
  BANNED: { label: '已停用', type: 'danger' },
}

export const VERIFY_STATUS: Record<VerifyStatus, { label: string; type: TagType }> = {
  NONE: { label: '未认证', type: 'info' },
  PENDING: { label: '待确认', type: 'warning' },
  VERIFIED: { label: '已认证', type: 'success' },
}

/** 打分方式的中文说明，匹配配置页用 */
export const SCORER_LABEL: Record<string, string> = {
  single: '相容矩阵',
  slider: '数值接近',
  tags: '标签重合',
  ranking: '排序相近',
  setIntersect: '有无交集',
  slotOverlap: '时段重叠',
  skillComplement: '技能补缺',
  roleComplement: '角色补缺',
  roleDiversity: '角色多样',
  mentorResource: '传帮带',
}

// ---------- 互评、信用、举报 ----------

export const REPORT_REASON: Record<ReportReason, string> = {
  PLAGIARISM: '抄袭',
  FORGERY: '伪造材料',
  ABUSE: '辱骂骚扰',
  SPAM: '广告刷屏',
  OTHER: '其他',
}

export const REPORT_STATUS: Record<ReportStatus, { label: string; type: TagType }> = {
  PENDING: { label: '待处理', type: 'warning' },
  CONFIRMED: { label: '属实', type: 'danger' },
  DISMISSED: { label: '不属实', type: 'info' },
}

export const REPORT_ACTION: Record<ReportAction, string> = {
  NONE: '只记录，不处理账号',
  SUSPEND_30: '暂停推荐 30 天',
  BLOCK: '不再出现在推荐里',
  BAN: '停用账号',
  CLOSE_TEAM: '关闭这支队的招募',
}

/** 信用分的颜色：85 以上绿色，70 以上橙色，再低红色 */
export function creditType(score: number): TagType {
  return score >= 85 ? 'success' : score >= 70 ? 'warning' : 'danger'
}

export const HISTORY_VISIBILITY: { value: HistoryVisibility; label: string; desc: string }[] = [
  { value: 'PUBLIC', label: '所有已认证的同学', desc: '找队友的人都能看到，更容易被邀请' },
  { value: 'RELATED', label: '只给队友和我主动联系过的人', desc: '队友、你申请加入的队伍，以及你的队伍邀请过的人才能看到' },
]

/** 资料页上组队记录的状态。离队不区分主动退出还是被移出 */
export const TEAM_RECORD_STATE: Record<TeamRecordState, { label: string; type: TagType }> = {
  IN_TEAM: { label: '在队中', type: 'primary' },
  FINISHED: { label: '已完赛', type: 'success' },
  LEFT: { label: '已离队', type: 'info' },
  DISBANDED: { label: '队伍已解散', type: 'info' },
}

export const RECOGNITION_STATUS: Record<RecognitionStatus, { label: string; type: TagType }> = {
  PENDING: { label: '等同学确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' },
}

/** 各校常见的认定写法，填的时候给个参考，也可以自己写 */
export const RECOGNITION_SUGGESTIONS = ['A 类', 'B 类', 'C 类', '一类竞赛', '二类竞赛', '三类竞赛', '国家级 A 类', '不认定']

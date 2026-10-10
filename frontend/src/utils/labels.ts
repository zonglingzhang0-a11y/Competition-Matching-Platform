// 枚举值对应的中文和标签颜色

import type { AppStatus, CompetitionLevel, EditionPhase, MeetingMode, NodeType, TeamStatus } from '@/types/api'

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

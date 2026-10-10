// 枚举值对应的中文和标签颜色

import type { CompetitionLevel, EditionPhase, NodeType } from '@/types/api'

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

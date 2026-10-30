import { get, post, postSlow } from './http'
import type { AiProfileAnalysis, AiTeamAdvice, SkillLevel } from '@/types/api'

/** 最近一次个人能力分析，没分析过返回 null */
export const getProfileAnalysis = () => get<AiProfileAnalysis | null>('/profile/ai-analysis')

/** 重新分析：模型配置好了就走模型，没配或连不上时后端退回本地规则，返回值里的 source 会写明 */
export const analyzeProfile = () => postSlow<AiProfileAnalysis>('/profile/ai-analysis')

/** 把勾选确认的技能写进自己的档案 */
export const applyAiSkills = (skills: { name: string; level: number }[]) =>
  post<SkillLevel[]>('/profile/ai-analysis/apply', { skills })

/** 队伍的招募建议，只有队长能调用；没分析过返回 null */
export const getRecruitAdvice = (teamId: number) => get<AiTeamAdvice | null>(`/teams/${teamId}/recruit-advice`)

export const analyzeRecruit = (teamId: number) => postSlow<AiTeamAdvice>(`/teams/${teamId}/recruit-advice`)

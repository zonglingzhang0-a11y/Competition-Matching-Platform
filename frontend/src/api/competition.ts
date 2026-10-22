import { del, get, post, postSlow, put } from './http'
import type {
  AiSummaryView,
  CompetitionDetail,
  CompetitionQuery,
  CompetitionSummary,
  Option,
  PageResult,
  RecognitionBoard,
  RecognitionForm,
} from '@/types/api'

export const listCompetitions = (query: CompetitionQuery) =>
  get<PageResult<CompetitionSummary>>('/competitions', { ...query })

export const competitionOptions = () => get<Option[]>('/competitions/options')

export const getCompetition = (id: number) => get<CompetitionDetail>(`/competitions/${id}`)

export const followEdition = (editionId: number) => post<void>(`/editions/${editionId}/follow`)

export const unfollowEdition = (editionId: number) => del<void>(`/editions/${editionId}/follow`)

// ---------- 各校加分认定 ----------

export const getRecognitions = (competitionId: number) => get<RecognitionBoard>(`/competitions/${competitionId}/recognitions`)

/** 提交或修改自己的标注，返回刷新后的整块 */
export const saveMyRecognition = (competitionId: number, form: RecognitionForm) =>
  put<RecognitionBoard>(`/competitions/${competitionId}/recognitions/mine`, form)

export const withdrawMyRecognition = (competitionId: number) =>
  del<RecognitionBoard>(`/competitions/${competitionId}/recognitions/mine`)

export const confirmRecognition = (id: number) => post<void>(`/recognitions/${id}/confirm`)

export const unconfirmRecognition = (id: number) => del<void>(`/recognitions/${id}/confirm`)

// ---------- AI 总结 ----------

export const getAiSummary = (competitionId: number) => get<AiSummaryView>(`/competitions/${competitionId}/ai-summary`)

/** 管理员：调大模型重新整理，本机模型要等几秒到几十秒 */
export const generateAiSummary = (competitionId: number) =>
  postSlow<AiSummaryView>(`/admin/competitions/${competitionId}/ai-summary`)

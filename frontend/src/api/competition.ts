import { del, get, post, put } from './http'
import type {
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

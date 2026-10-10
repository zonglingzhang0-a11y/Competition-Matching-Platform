import { del, get, post } from './http'
import type { CompetitionDetail, CompetitionQuery, CompetitionSummary, Option, PageResult } from '@/types/api'

export const listCompetitions = (query: CompetitionQuery) =>
  get<PageResult<CompetitionSummary>>('/competitions', { ...query })

export const competitionOptions = () => get<Option[]>('/competitions/options')

export const getCompetition = (id: number) => get<CompetitionDetail>(`/competitions/${id}`)

export const followEdition = (editionId: number) => post<void>(`/editions/${editionId}/follow`)

export const unfollowEdition = (editionId: number) => del<void>(`/editions/${editionId}/follow`)

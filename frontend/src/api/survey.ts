import { get, put } from './http'
import type { Answers, EditionAnswers, HomeRecommendation, MyAnswers, RecommendedUser, Survey, TeamRecommendations } from '@/types/api'

// 问卷
export const getSurvey = () => get<Survey>('/survey')
export const surveyProgress = () =>
  get<{ generalDone: boolean; generalQuick: boolean; editionsDone: number[] }>('/survey/progress')
export const myGeneralAnswers = () => get<MyAnswers | null>('/survey/general')
export const saveGeneralAnswers = (answers: Answers, quick: boolean) => put<void>('/survey/general', { answers, quick })
export const myEditionAnswers = (editionId: number) => get<EditionAnswers>(`/survey/editions/${editionId}`)
export const saveEditionAnswers = (editionId: number, answers: Answers, quick: boolean) =>
  put<void>(`/survey/editions/${editionId}`, { answers, quick })

// 推荐
export const recommendedTeams = (editionId: number) => get<TeamRecommendations>(`/editions/${editionId}/recommended-teams`)
export const recommendedUsers = (teamId: number) => get<RecommendedUser[]>(`/teams/${teamId}/recommended-users`)
export const homeRecommendations = () => get<HomeRecommendation[]>('/recommendations/home')

import { get, post, put } from './http'
import type { CreditView, MyReport, ReportReason, ReportTarget, RoundForm, RoundSummary, SubmitResult } from '@/types/api'

/** 队长标记比赛结束，返回新开的互评 id（单人队没有互评，返回 null） */
export const finishTeam = (teamId: number, award: string) =>
  post<number | null>(`/teams/${teamId}/finish`, { award: award || null })

export const myRounds = () => get<RoundSummary[]>('/reviews/mine')

export const reviewForm = (roundId: number) => get<RoundForm>(`/reviews/${roundId}`)

export const submitReview = (roundId: number, reviews: { rateeId: number; answers: number[] }[]) =>
  put<SubmitResult>(`/reviews/${roundId}`, { reviews })

export const myCredit = () => get<CreditView>('/credit/me')

export const createReport = (form: { targetType: ReportTarget; targetId: number; reason: ReportReason; detail: string }) =>
  post<number>('/reports', form)

export const myReports = () => get<MyReport[]>('/reports/mine')

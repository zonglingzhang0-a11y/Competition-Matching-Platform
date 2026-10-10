import { del, get, post, put } from './http'
import type {
  ApplicationView,
  MyApplications,
  NeedForm,
  Seeker,
  TeamDetail,
  TeamForm,
  TeamSummary,
} from '@/types/api'

// 队伍
export const createTeam = (editionId: number, form: TeamForm) => post<number>('/teams', { editionId, ...form })
export const myTeams = () => get<TeamSummary[]>('/teams/mine')
export const getTeam = (id: number) => get<TeamDetail>(`/teams/${id}`)
export const updateTeam = (id: number, form: TeamForm) => put<void>(`/teams/${id}`, form)
export const setRecruiting = (id: number, open: boolean) => put<void>(`/teams/${id}/recruiting`, { open })
export const disbandTeam = (id: number) => post<void>(`/teams/${id}/disband`)
export const leaveTeam = (id: number) => post<void>(`/teams/${id}/leave`)
export const removeMember = (id: number, userId: number) => del<void>(`/teams/${id}/members/${userId}`)
export const transferCaptain = (id: number, userId: number) => put<void>(`/teams/${id}/captain`, { userId })
export const editionTeams = (editionId: number) => get<TeamSummary[]>(`/editions/${editionId}/teams`)
export const teamSeekers = (id: number) => get<Seeker[]>(`/teams/${id}/seekers`)

// 招募需求
export const addNeed = (teamId: number, form: NeedForm) => post<number>(`/teams/${teamId}/needs`, form)
export const updateNeed = (needId: number, form: NeedForm) => put<void>(`/needs/${needId}`, form)
export const cancelNeed = (needId: number) => del<void>(`/needs/${needId}`)

// 申请和邀请
export const applyTeam = (teamId: number, needId: number | null, message: string) =>
  post<number>(`/teams/${teamId}/applications`, { needId, message })
export const inviteUser = (teamId: number, userId: number, needId: number | null, message: string) =>
  post<number>(`/teams/${teamId}/invitations`, { userId, needId, message })
export const teamApplications = (teamId: number) => get<ApplicationView[]>(`/teams/${teamId}/applications`)
export const myApplications = () => get<MyApplications>('/applications/mine')
export const acceptApplication = (id: number) => post<void>(`/applications/${id}/accept`)
export const rejectApplication = (id: number) => post<void>(`/applications/${id}/reject`)
export const withdrawApplication = (id: number) => post<void>(`/applications/${id}/withdraw`)

import { del, get, post, put } from './http'
import type { CalendarData, EventForm, EventView, MilestoneForm, TeamTimeline } from '@/types/api'

/** from、to 都包含，格式 2026-10-01 */
export const getCalendar = (from: string, to: string) => get<CalendarData>('/calendar', { from, to })

// 个人日程
export const getEvent = (id: number) => get<EventView>(`/events/${id}`)
export const createEvent = (form: EventForm) => post<number>('/events', form)
export const updateEvent = (id: number, form: EventForm) => put<void>(`/events/${id}`, form)
export const deleteEvent = (id: number) => del<void>(`/events/${id}`)
/** 重复日程只删某一天 */
export const skipOccurrence = (id: number, date: string) => post<void>(`/events/${id}/skip?date=${date}`)

// 队伍时间线
export const getTimeline = (teamId: number) => get<TeamTimeline>(`/teams/${teamId}/timeline`)
export const createMilestone = (teamId: number, form: MilestoneForm) => post<number>(`/teams/${teamId}/milestones`, form)
export const updateMilestone = (id: number, form: MilestoneForm) => put<void>(`/milestones/${id}`, form)
export const deleteMilestone = (id: number) => del<void>(`/milestones/${id}`)
export const setMilestoneDone = (id: number, done: boolean) => put<void>(`/milestones/${id}/done`, { done })

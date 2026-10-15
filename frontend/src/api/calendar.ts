import { del, get, post, put } from './http'
import type { CalendarData, EventForm, EventView, MilestoneForm, MyTask, TaskForm, TeamTimeline } from '@/types/api'

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

// ---------- 里程碑下的分工待办 ----------

export const createTask = (milestoneId: number, form: TaskForm) => post<number>(`/milestones/${milestoneId}/tasks`, form)
export const updateTask = (id: number, form: TaskForm) => put<void>(`/tasks/${id}`, form)
export const claimTask = (id: number) => post<void>(`/tasks/${id}/claim`)
export const releaseTask = (id: number) => post<void>(`/tasks/${id}/release`)
export const setTaskDone = (id: number, done: boolean) => put<void>(`/tasks/${id}/done`, { done })
export const deleteTask = (id: number) => del<void>(`/tasks/${id}`)
export const myTasks = () => get<MyTask[]>('/tasks/mine')

// ---------- 订阅到手机日历 ----------

/** path 拼上网站地址就是订阅链接，第一次调用时生成 */
export const getCalendarFeed = () => get<{ path: string }>('/calendar/feed')
/** 重置后旧链接马上失效 */
export const resetCalendarFeed = () => post<{ path: string }>('/calendar/feed/reset')

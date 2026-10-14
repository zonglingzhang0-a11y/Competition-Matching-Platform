import { get, post } from './http'
import type { Notification, PageResult } from '@/types/api'

export const listNotifications = (page: number, unreadOnly = false) =>
  get<PageResult<Notification>>('/notifications', { page, size: 20, unreadOnly })

export const unreadCount = () => get<number>('/notifications/unread-count')

export const markRead = (id: number) => post<void>(`/notifications/${id}/read`)

export const markAllRead = () => post<void>('/notifications/read-all')

/** 建立推送连接用的一次性票，30 秒内有效 */
export const pushTicket = () => post<{ ticket: string }>('/notifications/push-ticket')

// 点开一条消息跳到哪：消息页和右上角弹出的提示共用

import type { RouteLocationRaw } from 'vue-router'
import type { Notification } from '@/types/api'

/** 没有对应页面的消息（比如纯系统通知）返回 null */
export function notificationRoute(n: Notification): RouteLocationRaw | null {
  if (n.refType === 'TEAM' && n.refId) return { name: 'team', params: { id: n.refId } }
  if (n.refType === 'COMPETITION' && n.refId) return { name: 'competition', params: { id: n.refId } }
  if (n.refType === 'REVIEW' && n.refId) return { name: 'review-form', params: { id: n.refId } }
  if (n.refType === 'CREDIT') return { name: 'reviews' }
  if (n.refType === 'ADMIN_CREDIT') return { name: 'admin-reports', query: { tab: 'SUSPENDED' } }
  if (n.type === 'REPORT_RESULT') return { name: 'reviews', query: { tab: 'reports' } }
  if (n.type === 'VERIFY_RESULT') return { name: 'verify' }
  return null
}

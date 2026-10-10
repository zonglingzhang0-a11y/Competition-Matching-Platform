// 后端返回的时间是不带时区的 ISO 字符串（如 2026-10-15T23:59:59），按本地时间理解

import type { EndMode } from '@/types/api'

/** 比赛节点和队伍里程碑共用的时间字段，沿用简程的模型 */
export interface TimeSpan {
  startAt: string
  endAt: string | null
  allDay: boolean
  startLatestAt?: string | null
  endMode: EndMode
  endLatestAt: string | null
}

const pad = (n: number) => String(n).padStart(2, '0')

export function parse(iso: string): Date {
  return new Date(iso)
}

/** 2026-10-15；今年的省略年份：10-15 */
export function fmtDate(iso: string | null, alwaysYear = false): string {
  if (!iso) return ''
  const d = parse(iso)
  const md = `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return alwaysYear || d.getFullYear() !== new Date().getFullYear() ? `${d.getFullYear()}-${md}` : md
}

/** 10-15 23:59 */
export function fmtDateTime(iso: string | null, alwaysYear = false): string {
  if (!iso) return ''
  const d = parse(iso)
  return `${fmtDate(iso, alwaysYear)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fmtTime(iso: string): string {
  const d = parse(iso)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function sameDay(a: string, b: string): boolean {
  return parse(a).toDateString() === parse(b).toDateString()
}

/** 离截止还剩几天（向上取整），已过期返回负数 */
export function daysLeft(iso: string): number {
  return Math.ceil((parse(iso).getTime() - Date.now()) / 86_400_000)
}

/** 时间说不准的节点（开始或结束是一个区间、或者没有结束时间） */
export function isFuzzy(node: TimeSpan): boolean {
  return node.endMode !== 'FIXED' || !!node.startLatestAt
}

/** 节点时间的文字描述，时间不确定的写成区间，沿用简程的说法 */
export function nodeTimeText(node: TimeSpan): string {
  const show = (iso: string) => (node.allDay ? fmtDate(iso) : fmtDateTime(iso))
  let start = show(node.startAt)
  if (node.startLatestAt) start = `${start} 至 ${show(node.startLatestAt)} 之间开始`

  switch (node.endMode) {
    case 'RANGE':
      return node.endLatestAt && node.endAt
        ? `预计 ${fmtDate(node.endAt)} 至 ${fmtDate(node.endLatestAt)}`
        : start
    case 'OPEN':
      return `${start} 起，结束时间待定`
    default:
      if (!node.endAt || node.endAt === node.startAt) return start
      if (node.allDay) return `${fmtDate(node.startAt)} 至 ${fmtDate(node.endAt)}`
      return sameDay(node.startAt, node.endAt)
        ? `${start} – ${fmtTime(node.endAt)}`
        : `${start} – ${fmtDateTime(node.endAt)}`
  }
}

/** 节点是否已经过去：有结束时间看结束，没有看开始 */
export function isPast(node: TimeSpan): boolean {
  const last = node.endMode === 'RANGE' ? node.endLatestAt : (node.endAt ?? node.startAt)
  return node.endMode !== 'OPEN' && !!last && parse(last).getTime() < Date.now()
}

/** Date → 2026-10-12（本地时间） */
export function toDateStr(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Date → 2026-10-12T09:00:00（本地时间，不带时区，和后端的 LocalDateTime 对应） */
export function toLocalIso(d: Date): string {
  return `${toDateStr(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

export function addDays(d: Date, n: number): Date {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}

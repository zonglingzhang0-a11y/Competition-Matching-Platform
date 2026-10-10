// 后端返回的时间是不带时区的 ISO 字符串（如 2026-10-15T23:59:59），按本地时间理解

import type { CompetitionNode } from '@/types/api'

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
export function isFuzzy(node: CompetitionNode): boolean {
  return node.endMode !== 'FIXED' || node.startLatestAt !== null
}

/** 节点时间的文字描述，时间不确定的写成区间，沿用简程的说法 */
export function nodeTimeText(node: CompetitionNode): string {
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
export function isPast(node: CompetitionNode): boolean {
  const last = node.endMode === 'RANGE' ? node.endLatestAt : (node.endAt ?? node.startAt)
  return node.endMode !== 'OPEN' && !!last && parse(last).getTime() < Date.now()
}

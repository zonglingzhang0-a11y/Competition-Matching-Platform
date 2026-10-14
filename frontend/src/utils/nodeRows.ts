// 官方时间节点的编辑行：比赛库的"时间节点"弹窗和采用抓取通知的弹窗共用

import type { NodeForm } from '@/types/api'

/** 编辑时额外记一下"开始时间不确定"的勾选状态；fromNotice：从抓取的通知里新抽出来的，标出来 */
export type NodeRow = NodeForm & { fuzzyStart: boolean; fromNotice?: boolean }

/** 已有的节点（可以带 id 等别的字段）转成编辑行，只留表单字段 */
export function toRow(n: NodeForm): NodeRow {
  return {
    title: n.title,
    nodeType: n.nodeType,
    startAt: n.startAt,
    endAt: n.endAt,
    allDay: n.allDay,
    startLatestAt: n.startLatestAt,
    endMode: n.endMode,
    endLatestAt: n.endLatestAt,
    busy: n.busy,
    note: n.note,
    fuzzyStart: !!n.startLatestAt,
  }
}

export function emptyRow(): NodeRow {
  return {
    title: '',
    nodeType: 'OTHER',
    startAt: '',
    endAt: null,
    allDay: false,
    startLatestAt: null,
    endMode: 'FIXED',
    endLatestAt: null,
    busy: false,
    note: null,
    fuzzyStart: false,
  }
}

/** 第一个没填名称或开始时间的行（从 0 开始），都填了返回 -1 */
export function firstIncomplete(rows: NodeRow[]): number {
  return rows.findIndex((r) => !r.title.trim() || !r.startAt)
}

/** 提交给后端：只留表单字段，用不到的时间清空 */
export function toForms(rows: NodeRow[]): NodeForm[] {
  return rows.map((r) => ({
    title: r.title.trim(),
    nodeType: r.nodeType,
    startAt: r.startAt,
    endAt: r.endMode === 'OPEN' ? null : r.endAt,
    allDay: r.allDay,
    startLatestAt: r.fuzzyStart ? r.startLatestAt : null,
    endMode: r.endMode,
    endLatestAt: r.endMode === 'RANGE' ? r.endLatestAt : null,
    busy: r.busy,
    note: r.note?.trim() || null,
  }))
}

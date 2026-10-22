import type { InjectionKey } from 'vue'
// 相对路径：npm test 直接用 Node 跑这个文件，Node 不认 @/ 别名
import type { HomeLayout } from '../../types/api'

/**
 * 主页自定义：有哪些面板、各在哪一栏。面板的栏是固定的，用户能调的是同一栏里的先后、显示还是隐藏、日历栏宽窄。
 * 设置存在账号里（后端 HomeLayoutService），面板的 key 要和后端的 PANELS 一致。
 */
export type HomeColumn = 'top' | 'left' | 'middle' | 'right'

export interface HomePanel {
  key: string
  label: string
  column: HomeColumn
  /** 不能隐藏 */
  required?: boolean
}

export const HOME_PANELS: HomePanel[] = [
  { key: 'today', label: '下一个节点和本周时间', column: 'top' },
  { key: 'schedule', label: '赛程日历', column: 'left' },
  { key: 'semester', label: '学期赛程带', column: 'left' },
  { key: 'mine', label: '我参加的比赛', column: 'middle' },
  { key: 'open', label: '适合你报名', column: 'middle' },
  { key: 'pending', label: '待你处理', column: 'right', required: true },
  { key: 'recommend', label: '推荐队友 / 队伍（含匹配六边形）', column: 'right' },
  { key: 'messages', label: '消息', column: 'right' },
]

export const COLUMN_TITLE: Record<HomeColumn, string> = { top: '顶部', left: '左栏', middle: '中栏', right: '右栏' }

export const defaultLayout = (): HomeLayout => ({
  order: HOME_PANELS.map((p) => p.key),
  hidden: [],
  wideCalendar: false,
})

/** 子组件用：某个面板在本栏排第几（给 CSS order）、显不显示 */
export interface HomeLayoutApi {
  pos: (key: string) => number
  shown: (key: string) => boolean
}

export const HOME_LAYOUT: InjectionKey<HomeLayoutApi> = Symbol('homeLayout')

/** 没有放在主页里时（比如单独测试组件）：都显示，按原来的顺序 */
export const NO_LAYOUT: HomeLayoutApi = { pos: () => 0, shown: () => true }

/** 把 key 和同一栏里的上一个（dir = -1）或下一个（dir = 1）面板对调 */
export function moveInColumn(order: string[], key: string, dir: -1 | 1): string[] {
  const column = HOME_PANELS.find((p) => p.key === key)?.column
  const same = order.filter((k) => HOME_PANELS.find((p) => p.key === k)?.column === column)
  const other = same[same.indexOf(key) + dir]
  if (!other) return order
  const next = [...order]
  const a = next.indexOf(key)
  const b = next.indexOf(other)
  next[a] = other
  next[b] = key
  return next
}

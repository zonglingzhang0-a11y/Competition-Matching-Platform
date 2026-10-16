// 一句话添加个人日程：从一句中文里认出日期、时间、时长、重复和说不准的起止，剩下的当标题。
// 改自作者自己的日程应用"简程"（MIT）的 quickParse：去掉了桌面端才有的"智能完成"，
// 加上"周五前交初稿""3 天后""每周三……到 11 月 30 日为止"这几种说法。
// 不依赖项目里的其他文件，测试直接用 node --test 跑（见 __tests__/quickParse.test.ts）。

export type QuickRepeat = 'NONE' | 'DAILY' | 'WEEKDAYS' | 'WEEKLY'
/** 结束：确定 / 一个范围（endAt 到 endLatestAt）/ 做完为止 */
export type QuickEndMode = 'FIXED' | 'RANGE' | 'OPEN'

export interface QuickToken {
  kind: 'date' | 'time' | 'repeat' | 'warn'
  label: string
}

/** 字段和后端的 EventForm 对应，时间是不带时区的本地时间（2026-10-16T19:00:00） */
export interface QuickDraft {
  title: string
  startAt: string
  endAt: string | null
  allDay: boolean
  repeatType: QuickRepeat
  /** 1 = 周一 … 7 = 周日 */
  repeatWeekdays: number[]
  repeatUntil: string | null
  /** 说不准的开始：最晚哪天开始，没有就是 null */
  startLatestAt: string | null
  endMode: QuickEndMode
  endLatestAt: string | null
  /** 认出来的部分，显示在输入框下面给人核对 */
  tokens: QuickToken[]
}

// ---------- 日期小工具（日期一律用 2026-10-16 这样的字符串） ----------

const pad = (n: number): string => String(n).padStart(2, '0')

function toKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function fromKey(key: string): Date {
  const [y = 1970, m = 1, d = 1] = key.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function addDays(key: string, n: number): string {
  const d = fromKey(key)
  d.setDate(d.getDate() + n)
  return toKey(d)
}

const dayDiff = (a: string, b: string): number => Math.round((fromKey(b).getTime() - fromKey(a).getTime()) / 86_400_000)
const dow = (key: string): number => fromKey(key).getDay()
const hhmm = (min: number): string => `${pad(Math.floor(min / 60))}:${pad(min % 60)}`

/** 2 月 30 日这种不存在的日期返回 false */
function validKey(key: string): boolean {
  const x = fromKey(key)
  return !Number.isNaN(x.getTime()) && toKey(x) === key
}

// ---------- 中文的说法 ----------

const WD: Record<string, number> = { 日: 0, 天: 0, 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6 }
const WD_LABEL = ['日', '一', '二', '三', '四', '五', '六']
const CN_NUM: Record<string, number> = { 半: 0.5, 一: 1, 两: 2, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }
const wd = (c: string | undefined): number => WD[c ?? ''] ?? 0

/** 「十二」「二十」「两」这样的中文数字 */
function cnNum(w: string): number {
  if (/^\d+$/.test(w)) return Number(w)
  const m = w.match(/^([一两二三四五六七八九])?十([一二三四五六七八九])?$/)
  if (m) return (m[1] ? (CN_NUM[m[1]] ?? 1) : 1) * 10 + (m[2] ? (CN_NUM[m[2]] ?? 0) : 0)
  return CN_NUM[w] ?? NaN
}

const NUM = '(\\d{1,3}|[一两二三四五六七八九]?十[一二三四五六七八九]?|[一两二三四五六七八九])'
const PERIOD = '(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|今晚|晚|早)?'
/** 中文的钟点数：两点、十二点 */
const CN_HOUR = '(十[一二]?|[一两二三四五六七八九])点'
const POINT = '(\\d{1,2})(?:[:：](\\d{2})|点(?:(半)|(\\d{1,2})分?)?)'
const SEP = '(?:到|至|-|–|—|~|～)'
/** 能当作日期的说法（用于「A 到 B」） */
const DW =
  '(?:大后天|后天|明天|明日|今天|今日|下个?(?:周|星期|礼拜)[一二三四五六日天]|(?:这|本)?(?:周|星期|礼拜)[一二三四五六日天]|\\d{1,2}月\\d{1,2}[日号]?|\\d{1,2}[日号])'
/** 日期后面紧跟的"前""之前"：周五前交初稿。「前端」「前期」这类词不算 */
const BEFORE = '(?:之前|以前|前(?![端台期往面后沿夕景]))?'
/** 最长 31 天，和后端一致 */
const MAX_MINUTES = 31 * 1440
/** 说不准的日程：从最早开始到最晚结束最多 90 天，和后端一致 */
const MAX_FUZZY_DAYS = 90

// ---- 说不准的起止（简程）：下个月找时间开始、大概两周、月底前做完、做完为止 ----

/** 时长里的数，比 NUM 多一个"半"（半个月） */
const LEN_NUM = '(\\d{1,3}|[一两二三四五六七八九]?十[一二三四五六七八九]?|[半一两二三四五六七八九])'
const LEN_UNIT = '(?:个)?(天|周|星期|礼拜|月)'
const unitDays = (u: string | undefined): number => (u === '天' ? 1 : u === '月' ? 30 : 7)
/** 一段说不准具体哪天的时间：下个月、下周、10 月中旬、月底 */
const PERIOD_W =
  '下个?月|下个?(?:周|星期|礼拜)(?![一二三四五六日天])|\\d{1,2}月(?:初|上旬|中旬|中|下旬|底|末)|(?:这个?|本)?月(?:初|中旬|下旬|底|末)'

function periodOf(w: string, today: string): [string, string] | null {
  const t = fromKey(today)
  if (/^下个?月$/.test(w)) {
    return [toKey(new Date(t.getFullYear(), t.getMonth() + 1, 1)), toKey(new Date(t.getFullYear(), t.getMonth() + 2, 0))]
  }
  if (/^下个?(?:周|星期|礼拜)$/.test(w)) {
    const monday = addDays(today, -((dow(today) + 6) % 7) + 7)
    return [monday, addDays(monday, 6)]
  }
  const m = w.match(/^(?:(\d{1,2})月|(?:这个?|本)?月)(初|上旬|中旬|中|下旬|底|末)$/)
  if (!m) return null
  const month = m[1] ? Number(m[1]) - 1 : t.getMonth()
  if (month > 11) return null
  const part = m[2] ?? ''
  const [d0, d1] = /初|上旬/.test(part) ? [1, 10] : /中/.test(part) ? [11, 20] : [21, 0]
  const range = (y: number): [string, string] => [
    toKey(new Date(y, month, d0)),
    toKey(d1 ? new Date(y, month, d1) : new Date(y, month + 1, 0)),
  ]
  let r = range(t.getFullYear())
  if (r[1] < today) r = range(t.getFullYear() + 1)
  return [r[0] < today ? today : r[0], r[1]]
}

/** 今天 / 明天 / 后天 / 10月8日 */
function shortLabel(date: string, today: string): string {
  if (date === today) return '今天'
  if (date === addDays(today, 1)) return '明天'
  if (date === addDays(today, 2)) return '后天'
  const d = fromKey(date)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

function toHour(h: number, period: string | undefined, fallbackPm: boolean): number {
  if (period) {
    if (/下午|傍晚|晚/.test(period)) return h < 12 ? h + 12 : h
    if (period === '中午') return h <= 3 ? h + 12 : h
    if (period === '凌晨') return h === 12 ? 0 : h
    return h
  }
  // 没说上午下午时，1–6 点通常指下午
  return fallbackPm && h >= 1 && h <= 6 ? h + 12 : h
}

const pointMin = (mm?: string, half?: string, mm2?: string): number => Number(mm ?? mm2 ?? (half ? 30 : 0))

/** 10月16日 周四；今天、明天、后天直接说 */
function relLabel(date: string, today: string): string {
  if (date === today) return '今天'
  if (date === addDays(today, 1)) return '明天'
  if (date === addDays(today, 2)) return '后天'
  const d = fromKey(date)
  return `${d.getMonth() + 1}月${d.getDate()}日 周${WD_LABEL[d.getDay()]}`
}

/**
 * 把一个日期说法换成日期；from 不为空时表示「A 到 B」里的 B，取 from 当天或之后最近的那天。
 * keepPast：「9 月 19 日到 21 日」这种正在进行的时段，开始日期可以在今天之前
 */
function dateWord(w: string, today: string, from: string | null, keepPast = false): string | null {
  const rel = ({ 今天: 0, 今日: 0, 明天: 1, 明日: 1, 后天: 2, 大后天: 3 } as Record<string, number>)[w]
  if (rel !== undefined) return addDays(today, rel)
  const ref = from ?? today
  let m = w.match(/^下个?(?:周|星期|礼拜)([一二三四五六日天])$/)
  if (m) {
    const monday = addDays(today, -((dow(today) + 6) % 7) + 7)
    return addDays(monday, (wd(m[1]) + 6) % 7)
  }
  m = w.match(/^(这|本)?(?:周|星期|礼拜)([一二三四五六日天])$/)
  if (m) {
    if (from) return addDays(from, (wd(m[2]) - dow(from) + 7) % 7)
    const monday = addDays(today, -((dow(today) + 6) % 7))
    const d = addDays(monday, (wd(m[2]) + 6) % 7)
    return !m[1] && d < today ? addDays(d, 7) : d
  }
  const y = fromKey(ref).getFullYear()
  m = w.match(/^(\d{1,2})月(\d{1,2})[日号]?$/)
  if (m) {
    let d = `${y}-${pad(Number(m[1]))}-${pad(Number(m[2]))}`
    if (d < ref && !keepPast) d = `${y + 1}-${pad(Number(m[1]))}-${pad(Number(m[2]))}`
    return validKey(d) ? d : null
  }
  m = w.match(/^(\d{1,2})[日号]$/)
  if (m) {
    const r = fromKey(ref)
    let d = `${y}-${pad(r.getMonth() + 1)}-${pad(Number(m[1]))}`
    if (d < ref && !keepPast) d = toKey(new Date(r.getFullYear(), r.getMonth() + 1, Number(m[1])))
    return validKey(d) ? d : null
  }
  return null
}

/** today：2026-10-16 这样的本地日期 */
export function parseQuick(input: string, today: string): QuickDraft {
  let s = ` ${input.trim()} `
  const tokens: QuickToken[] = []
  const take = (re: RegExp): RegExpMatchArray | null => {
    const m = s.match(re)
    if (m) s = s.replace(m[0], ' ')
    return m
  }
  /** 拿走日期时连同后面的"前"一起拿走 */
  const takeDate = (re: string): RegExpMatchArray | null => take(new RegExp(`(?:${re})${BEFORE}`))

  // ---- 重复 ----
  let repeatType: QuickRepeat = 'NONE'
  let weekdays: number[] = []
  let repeatLabel = ''
  if (take(/每个?工作日|工作日/)) {
    repeatType = 'WEEKDAYS'
    repeatLabel = '工作日'
  } else if (take(/每天|每日|天天/)) {
    repeatType = 'DAILY'
    repeatLabel = '每天'
  } else if (take(/每个?周末/)) {
    repeatType = 'WEEKLY'
    weekdays = [6, 7]
    repeatLabel = '每周末'
  } else {
    const weekly = take(/每个?(?:周|星期|礼拜)([一二三四五六日天](?:[、,，和及]?[一二三四五六日天])*)?/)
    if (weekly) {
      repeatType = 'WEEKLY'
      const days = weekly[1] ? [...new Set([...weekly[1]].filter((c) => c in WD).map((c) => wd(c) || 7))] : []
      weekdays = days.sort((a, b) => a - b)
      repeatLabel = weekdays.length ? `每周${weekdays.map((d) => WD_LABEL[d % 7]).join('、')}` : '每周'
    }
  }

  // ---- 中文数字的钟点换成阿拉伯数字：「下午两点半」「晚上七点到九点」 ----
  // 只换前面有"上午、晚上"这类词的，和时间段里"到"后面的那个；「早一点出发」「提两点意见」不动
  s = s.replace(
    new RegExp(`(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|今晚)(\\s*)${CN_HOUR}`, 'g'),
    (_m: string, p: string, sp: string, n: string) => `${p}${sp}${cnNum(n)}点`,
  )
  s = s.replace(
    new RegExp(`(\\d点(?:半|\\d{1,2}分?)?\\s*${SEP}\\s*(?:凌晨|早上|早晨|上午|中午|下午|傍晚|晚上)?\\s*)${CN_HOUR}`, 'g'),
    (_m: string, head: string, n: string) => `${head}${cnNum(n)}点`,
  )

  // ---- 跨天的说法：「到明早 7 点」「到次日 7 点」统一成「次日」，避免「明天」被当成日期 ----
  s = s.replace(
    new RegExp(`(${SEP})\\s*(?:明天|明日|明早|次日|第二天|隔天)\\s*(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上)?(?=\\s*\\d)`),
    (m0: string, sep: string, p?: string) => `${sep} 次日${p ?? (/明早/.test(m0) ? '早上' : '')}`,
  )

  // ---- 日期 ----
  let date = today
  let dateFound = false
  let allDay = false
  let days = 0
  let start: number | null = null
  /** 结束时间，从开始那天的 0 点算起的分钟数（跨天时会超过 1440） */
  let end: number | null = null
  let until: string | null = null
  const setDate = (d: string): void => {
    date = d
    dateFound = true
  }
  /** 「A 到 B」：正在进行的时段（B 还没过）保留今天之前的开始日期，整段都过去了才往后顺延 */
  const rangeOf = (w1: string, w2: string): [string, string] | null => {
    const a = dateWord(w1, today, null, true)
    const b = a && dateWord(w2, today, a)
    if (a && b && b >= today) return [a, b]
    const c = dateWord(w1, today, null)
    const d = c && dateWord(w2, today, c)
    return c && d ? [c, d] : null
  }

  let m: RegExpMatchArray | null
  if (repeatType !== 'NONE') {
    // 重复的日程：「从 10 月 20 日到 11 月 30 日」是重复的起止，「到 11 月 30 日为止」是重复到哪天
    if ((m = take(new RegExp(`从?(${DW})\\s*${SEP}\\s*(${DW})(?:为止|结束)?`)))) {
      const r = rangeOf(m[1] ?? '', m[2] ?? '')
      if (r) {
        setDate(r[0])
        until = r[1]
      }
    } else if ((m = take(/(?:一直|直)?(?:到|至|截至|截止到?)\s*(\d{1,2}月\d{1,2}[日号]?|\d{1,2}[日号])\s*(?:为止|结束)?/))) {
      until = dateWord(m[1] ?? '', today, null)
    }
  }

  // 周五晚上 6 点到周日晚上 8 点：跨好几天、带时间
  if (!dateFound && (m = take(new RegExp(`(${DW})\\s*${PERIOD}\\s*${POINT}\\s*${SEP}\\s*(${DW})\\s*${PERIOD}\\s*${POINT}`)))) {
    const [, w1 = '', p1, h1, m1, half1, mm1, w2 = '', p2, h2, m2, half2, mm2] = m
    const r = rangeOf(w1, w2)
    if (r) {
      const hasPeriod = !!(p1 || p2)
      setDate(r[0])
      start = toHour(Number(h1), p1, !hasPeriod) * 60 + pointMin(m1, half1, mm1)
      end = dayDiff(r[0], r[1]) * 1440 + toHour(Number(h2), p2, !hasPeriod) * 60 + pointMin(m2, half2, mm2)
    }
  } else if (!dateFound && (m = take(new RegExp(`从?(${DW})\\s*${SEP}\\s*(${DW})`)))) {
    // 周五到周日、9 月 19 日到 21 日：全天、跨好几天
    const r = rangeOf(m[1] ?? '', m[2] ?? '')
    if (r) {
      setDate(r[0])
      allDay = true
      days = Math.max(0, dayDiff(r[0], r[1]))
    }
  } else if (!dateFound && (m = takeDate('大后天|后天|明天|明日|今天|今日'))) {
    setDate(addDays(today, { 今天: 0, 今日: 0, 明天: 1, 明日: 1, 后天: 2, 大后天: 3 }[m[0].replace(/(?:之前|以前|前)$/, '')] ?? 0))
  } else if (!dateFound && (m = takeDate(`${NUM}\\s*天(?:之后|以后|后)`))) {
    // 3 天后、两天后
    const n = cnNum(m[1] ?? '')
    if (n >= 1) setDate(addDays(today, Math.round(n)))
  } else if (!dateFound && (m = takeDate('下个?(?:周|星期|礼拜)([一二三四五六日天])'))) {
    const monday = addDays(today, -((dow(today) + 6) % 7) + 7)
    setDate(addDays(monday, (wd(m[1]) + 6) % 7))
  } else if (!dateFound && (m = takeDate('(这|本)?(?:周|星期|礼拜)([一二三四五六日天])'))) {
    const monday = addDays(today, -((dow(today) + 6) % 7))
    let d = addDays(monday, (wd(m[2]) + 6) % 7)
    if (!m[1] && d < today) d = addDays(d, 7)
    setDate(d)
  } else if (!dateFound && (m = takeDate('周末'))) {
    const w = dow(today)
    setDate(w === 0 ? today : addDays(today, 6 - w))
  } else if (!dateFound && (m = takeDate('(\\d{1,2})月(\\d{1,2})[日号]?') ?? takeDate('(?<![\\d:：])(\\d{1,2})\\/(\\d{1,2})(?![\\d:：])'))) {
    const y = fromKey(today).getFullYear()
    let d = `${y}-${pad(Number(m[1]))}-${pad(Number(m[2]))}`
    if (d < today) d = `${y + 1}-${pad(Number(m[1]))}-${pad(Number(m[2]))}`
    if (validKey(d)) setDate(d)
  } else if (!dateFound && (m = takeDate('(?<![\\d月])(\\d{1,2})[号日](?!\\d)'))) {
    const t = fromKey(today)
    let d = `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(Number(m[1]))}`
    if (d < today) d = toKey(new Date(t.getFullYear(), t.getMonth() + 1, Number(m[1])))
    if (validKey(d)) setDate(d)
  }

  // 「明天起」「从周五开始」里的「起 / 开始」不算标题
  if (dateFound) s = s.replace(/^\s*从?\s*(?:起|开始)(?=\s)/, ' ')

  // ---- 时间 ----
  if (start === null && !allDay) {
    const range = take(new RegExp(`${PERIOD}\\s*${POINT}\\s*${SEP}\\s*(次日)?\\s*${PERIOD}\\s*${POINT}`))
    if (range) {
      const [, p1, h1, m1, half1, mm1, nextDay, p2, h2, m2, half2, mm2] = range
      const hasPeriod = !!(p1 || p2)
      start = toHour(Number(h1), p1, !hasPeriod) * 60 + pointMin(m1, half1, mm1)
      end = toHour(Number(h2), p2 ?? (nextDay ? undefined : p1), !hasPeriod) * 60 + pointMin(m2, half2, mm2)
      if (nextDay) end += 1440
      else if (end <= start && end + 720 > start) end += 720
      // 结束比开始还早：过了午夜，算到第二天
      if (end <= start) end += 1440
      if (p1 === '今晚' && !dateFound) setDate(today)
    } else {
      const single = take(new RegExp(`${PERIOD}\\s*${POINT}`))
      if (single) {
        const [, p, h, mm, half, mm2] = single
        start = toHour(Number(h), p, true) * 60 + pointMin(mm, half, mm2)
        if (p === '今晚' && !dateFound) setDate(today)
      }
    }
  }
  if (start !== null && (start >= 1440 || (end !== null && (end <= start || end - start > MAX_MINUTES)))) {
    start = null
    end = null
  }

  // ---- 说不准的开始和结束（没有具体时间的事才有）----
  let endMode: QuickEndMode = 'FIXED'
  /** 最晚在开始那天之后第几天开始 */
  let startMax = 0
  /** 预计的总天数范围 */
  let length: [number, number] | null = null
  /** 预计结束的日期范围 */
  let endPeriod: [string, string] | null = null
  if (start === null) {
    // 「找到为止」「做完为止」：还没定结束
    if (take(/(?:[做找读写搞弄学看改修练考背装][完到好定会成齐]|完成|结束)为止/)) endMode = 'OPEN'
    // 「预计 10 月初结束」「月底前搞定」
    const ep = take(
      new RegExp(`(?:预计|大概|估计|争取|最晚)?\\s*在?\\s*(${PERIOD_W})\\s*(?:之?前)?\\s*(?:结束|完成|搞定|做完|完工|弄完|交)`),
    )
    if (ep) endPeriod = periodOf(ep[1] ?? '', today)
    // 「下个月找个时间开始」「10 月中旬开工」
    const sp = take(new RegExp(`从?\\s*(${PERIOD_W})\\s*(?:找个?时间|找一天|抽空|某天|的时候)?\\s*(?:开始|动工|开工|启动|动手)`))
    const sr = sp && periodOf(sp[1] ?? '', today)
    if (sr) {
      setDate(sr[0])
      startMax = Math.max(0, dayDiff(sr[0], sr[1]))
    }
    // 「3 到 5 天」「两到三周」
    const rg = take(
      new RegExp(`(?:大概|大约|预计|估计|差不多|约莫?)?\\s*(?:要|需要|得)?\\s*${LEN_NUM}\\s*${SEP}\\s*${LEN_NUM}\\s*${LEN_UNIT}(?:左右)?`),
    )
    if (rg) {
      const u = unitDays(rg[3])
      const a = Math.round(cnNum(rg[1] ?? '') * u)
      const b = Math.round(cnNum(rg[2] ?? '') * u)
      if (a > 0 && b > a) length = [a, b]
    }
    // 「大概两周」「预计一个月左右」：前后各留四分之一
    const ab =
      !length &&
      take(
        new RegExp(
          `(?:大概|大约|预计|估计|差不多|约莫?)\\s*(?:要|需要|得)?\\s*${LEN_NUM}\\s*${LEN_UNIT}(?:左右)?|${LEN_NUM}\\s*${LEN_UNIT}左右`,
        ),
      )
    if (ab) {
      const total = Math.round(cnNum(ab[1] ?? ab[3] ?? '') * unitDays(ab[2] ?? ab[4]))
      if (total > 0) {
        const d = Math.max(1, Math.round(total / 4))
        length = [Math.max(1, total - d), total + d]
      }
    }
    if (endMode !== 'FIXED' || endPeriod || startMax || length) allDay = true
  }

  // ---- 全天、连续几天 ----
  if (start === null) {
    if (take(/全天|一整天/)) allDay = true
    const n =
      take(/(?:连续|持续|为期)?\s*(\d{1,2}|[两二三四五六七八九十])\s*天(?![后前内])/) ??
      take(new RegExp(`(?:连续|持续|为期)\\s*${NUM}\\s*(?:个)?(周|星期|礼拜)`))
    if (n) {
      allDay = true
      const total = Math.round(cnNum(n[1] ?? '') * (n[2] ? 7 : 1))
      if (total >= 1) days = Math.min(30, total - 1)
      if (startMax && !length) length = [days + 1, days + 1]
    }
  }

  // 把说不准的部分换成相对开始那天的天数：最早结束不早于最晚开始
  let daysMax = 0
  if (allDay) {
    if (endPeriod && endPeriod[1] >= date) {
      days = Math.max(startMax, dayDiff(date, endPeriod[0]))
      daysMax = Math.max(days, dayDiff(date, endPeriod[1]))
      endMode = daysMax > days ? 'RANGE' : 'FIXED'
    } else if (length) {
      days = Math.max(startMax, length[0] - 1)
      daysMax = Math.max(days, startMax + length[1] - 1)
      endMode = daysMax > days ? 'RANGE' : 'FIXED'
    } else if (startMax && endMode === 'FIXED') {
      // 只说了什么时候开始，没说多久：结束先算没定
      endMode = 'OPEN'
    }
    if (daysMax > MAX_FUZZY_DAYS - 1) {
      daysMax = MAX_FUZZY_DAYS - 1
      days = Math.min(days, daysMax)
      tokens.push({ kind: 'warn', label: `太长了，按最多 ${MAX_FUZZY_DAYS} 天算` })
    }
  }
  const fuzzy = startMax > 0 || endMode !== 'FIXED'

  // ---- 时长（有具体开始时间、没说结束时才算）：2 小时、90 分钟、一个半小时 ----
  if (start !== null && end === null) {
    const dur =
      take(/(\d+|[一两二三四五六])个半(?:小时|钟头)/) ??
      take(/(\d+(?:\.\d+)?|[半一两二三四五六])\s*个?\s*(?:小时|钟头|h\b)(?:\s*(\d{1,2})\s*分钟?)?/i) ??
      take(/(\d{1,3})\s*(?:分钟|min\b)/i)
    let minutes = 60
    if (dur) {
      const raw = dur[0]
      const n = (v: string | undefined): number => CN_NUM[v ?? ''] ?? Number(v)
      if (/个半/.test(raw)) minutes = (n(dur[1]) + 0.5) * 60
      else if (/小时|钟头|h/i.test(raw)) minutes = n(dur[1]) * 60 + Number(dur[2] ?? 0)
      else minutes = Number(dur[1])
      minutes = Math.max(5, Math.min(MAX_MINUTES, Math.round(minutes)))
    }
    end = start + minutes
  }

  // 没有具体时间的都算全天
  if (start === null) allDay = true

  // ---- 重复的收尾 ----
  const multiDay = allDay ? days > 0 : end !== null && start !== null && end - start >= 1440
  if ((multiDay || fuzzy) && repeatType !== 'NONE') {
    repeatType = 'NONE'
    weekdays = []
    until = null
    tokens.push({ kind: 'warn', label: fuzzy ? '时间说不准的日程不能重复，已按一次算' : '跨天的日程不能重复，已按一次算' })
    repeatLabel = ''
  }
  // 每周但没说星期几：按开始那天
  if (repeatType === 'WEEKLY' && !weekdays.length) {
    const w = dow(date) || 7
    weekdays = [w]
    repeatLabel = `每周${WD_LABEL[w % 7]}`
  }
  // 开始那天挪到第一次真正发生的那天（每周三、五从周四说起，第一次是周五）
  const occurs = (d: string): boolean => {
    const w = dow(d) || 7
    return repeatType === 'WEEKDAYS' ? w <= 5 : weekdays.includes(w)
  }
  if (repeatType === 'WEEKLY' || repeatType === 'WEEKDAYS') {
    for (let i = 0; i < 7 && !occurs(date); i++) date = addDays(date, 1)
  }
  if (until && until < date) until = null

  // ---- 组装 ----
  const title = s
    .replace(/\s+/g, ' ')
    .replace(/^[\s，,。、:：]+|[\s，,。、:：]+$/g, '')
    .trim()

  let startAt: string
  let endAt: string | null
  let startLatestAt: string | null = null
  let endLatestAt: string | null = null
  const dayAt = (k: number): string => `${addDays(date, k)}T00:00:00`
  if (allDay && fuzzy) {
    startAt = dayAt(0)
    startLatestAt = startMax ? dayAt(startMax) : null
    endAt = endMode === 'OPEN' ? null : dayAt(days)
    endLatestAt = endMode === 'RANGE' ? dayAt(daysMax) : null
    const sl = (k: number): string => shortLabel(addDays(date, k), today)
    tokens.unshift(
      { kind: 'date', label: startMax ? `${sl(0)}–${sl(startMax)} 之间开始` : `${sl(0)}起` },
      {
        kind: 'date',
        label: endMode === 'OPEN' ? '做完为止' : endMode === 'RANGE' ? `预计 ${sl(days)}–${sl(daysMax)} 结束` : `${sl(days)} 结束`,
      },
    )
  } else if (allDay) {
    startAt = dayAt(0)
    endAt = days > 0 ? dayAt(days) : null
    const label = days ? `${relLabel(date, today)} – ${relLabel(addDays(date, days), today)} · ${days + 1} 天` : `${relLabel(date, today)} 全天`
    tokens.unshift({ kind: 'date', label: repeatType === 'NONE' ? label : `从${relLabel(date, today)}起` })
  } else {
    const s0 = start ?? 0
    const e0 = end ?? s0 + 60
    const endDate = addDays(date, Math.floor(e0 / 1440))
    startAt = `${date}T${hhmm(s0)}:00`
    endAt = `${endDate}T${hhmm(e0 % 1440)}:00`
    const endLabel = e0 <= 1440 ? hhmm(e0) : e0 <= 2880 ? `次日 ${hhmm(e0 - 1440)}` : `${relLabel(endDate, today)} ${hhmm(e0 % 1440)}`
    tokens.unshift(
      { kind: 'date', label: repeatType === 'NONE' ? relLabel(date, today) : `从${relLabel(date, today)}起` },
      { kind: 'time', label: `${hhmm(s0)}–${endLabel}` },
    )
  }
  if (repeatLabel) {
    const u = until ? fromKey(until) : null
    tokens.push({ kind: 'repeat', label: u ? `${repeatLabel}，到 ${u.getMonth() + 1}月${u.getDate()}日` : repeatLabel })
  }

  return {
    title,
    startAt,
    endAt,
    allDay,
    repeatType,
    repeatWeekdays: weekdays,
    repeatUntil: until,
    startLatestAt,
    endMode,
    endLatestAt,
    tokens,
  }
}

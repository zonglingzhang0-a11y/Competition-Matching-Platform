// 一句话添加的解析测试：npm test（Node 自带的测试，不用装别的）
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseQuick, type QuickDraft } from '../quickParse.ts'

/** 2026-10-15 是周四 */
const TODAY = '2026-10-15'

function check(input: string, expected: Partial<QuickDraft>) {
  const got = parseQuick(input, TODAY)
  for (const [key, value] of Object.entries(expected)) {
    assert.deepEqual(got[key as keyof QuickDraft], value, `「${input}」的 ${key}`)
  }
  return got
}

test('日期加时间段', () => {
  check('明天下午3点到5点 队伍开会', {
    title: '队伍开会',
    startAt: '2026-10-16T15:00:00',
    endAt: '2026-10-16T17:00:00',
    allDay: false,
    repeatType: 'NONE',
  })
  check('下周一 9:30 和陈一舟对论文 1个半小时', {
    title: '和陈一舟对论文',
    startAt: '2026-10-19T09:30:00',
    endAt: '2026-10-19T11:00:00',
  })
  check('周日 下午2点 2小时 组会', { title: '组会', startAt: '2026-10-18T14:00:00', endAt: '2026-10-18T16:00:00' })
})

test('中文数字的钟点', () => {
  check('下周三下午两点半 答辩', { title: '答辩', startAt: '2026-10-21T14:30:00', endAt: '2026-10-21T15:30:00' })
  check('晚上七点到九点 组会', { title: '组会', startAt: '2026-10-15T19:00:00', endAt: '2026-10-15T21:00:00' })
  check('明天上午十点到十一点半 模拟答辩', { startAt: '2026-10-16T10:00:00', endAt: '2026-10-16T11:30:00' })
  // 不是钟点的"一点""两点"不动
  check('明天早一点出发去考场', { title: '早一点出发去考场', allDay: true })
  check('周五 给队长提两点意见', { title: '给队长提两点意见', allDay: true })
})

test('只说了开始时间，默认一小时', () => {
  check('周三晚上8点 和指导老师见面', { startAt: '2026-10-21T20:00:00', endAt: '2026-10-21T21:00:00' })
})

test('没说时间的算全天', () => {
  check('3天后 交报名表', { title: '交报名表', startAt: '2026-10-18T00:00:00', endAt: null, allDay: true })
  check('10月20日到22日 省赛', { title: '省赛', startAt: '2026-10-20T00:00:00', endAt: '2026-10-22T00:00:00', allDay: true })
  check('连续3天 校内选拔赛 10月24日', { title: '校内选拔赛', startAt: '2026-10-24T00:00:00', endAt: '2026-10-26T00:00:00' })
})

test('日期后面的"前"不算标题，"前端"这样的词保留', () => {
  check('周五前交论文初稿', { title: '交论文初稿', startAt: '2026-10-16T00:00:00', allDay: true })
  check('明天前端联调', { title: '前端联调', startAt: '2026-10-16T00:00:00' })
  check('交初稿 10月31日之前', { title: '交初稿', startAt: '2026-10-31T00:00:00' })
})

test('跨天和过午夜', () => {
  check('周五晚上6点到周日晚上8点 校赛集训营', {
    title: '校赛集训营',
    startAt: '2026-10-16T18:00:00',
    endAt: '2026-10-18T20:00:00',
    allDay: false,
  })
  check('今晚11点到明早1点 赶论文', { title: '赶论文', startAt: '2026-10-15T23:00:00', endAt: '2026-10-16T01:00:00' })
  check('今晚10点到12点 对答案', { startAt: '2026-10-15T22:00:00', endAt: '2026-10-16T00:00:00' })
})

test('今年已经过去的日期算明年', () => {
  check('3月1日 美赛报名', { title: '美赛报名', startAt: '2027-03-01T00:00:00' })
})

test('重复', () => {
  check('每周三、五晚上7点到9点 数模集训 到11月30日', {
    title: '数模集训',
    // 今天周四，第一次是明天周五
    startAt: '2026-10-16T19:00:00',
    endAt: '2026-10-16T21:00:00',
    repeatType: 'WEEKLY',
    repeatWeekdays: [3, 5],
    repeatUntil: '2026-11-30',
  })
  check('每周六 从10月17日到11月28日 上午9点到12点 集训', {
    title: '集训',
    startAt: '2026-10-17T09:00:00',
    endAt: '2026-10-17T12:00:00',
    repeatType: 'WEEKLY',
    repeatWeekdays: [6],
    repeatUntil: '2026-11-28',
  })
  check('工作日 早上7点 晨读', { title: '晨读', repeatType: 'WEEKDAYS', startAt: '2026-10-15T07:00:00' })
  check('每个周末 下午2点到5点 刷题', { repeatType: 'WEEKLY', repeatWeekdays: [6, 7], startAt: '2026-10-17T14:00:00' })
  check('每周日晚上9点 周总结', { repeatWeekdays: [7], startAt: '2026-10-18T21:00:00' })
  check('工作日 从10月17日起 早上7点 晨读', { repeatType: 'WEEKDAYS', startAt: '2026-10-19T07:00:00' })
  // 没说星期几：按开始那天（今天周四）
  check('每周 组会 晚上8点', { title: '组会', repeatType: 'WEEKLY', repeatWeekdays: [4] })
})

test('跨天的日程不重复', () => {
  const got = check('每天 连续3天 冲刺', { title: '冲刺', repeatType: 'NONE', endAt: '2026-10-17T00:00:00' })
  assert.ok(got.tokens.some((t) => t.kind === 'warn'))
})

test('识别结果给人核对', () => {
  const got = parseQuick('明天下午3点到5点 队伍开会', TODAY)
  assert.deepEqual(
    got.tokens.map((t) => t.label),
    ['明天', '15:00–17:00'],
  )
  assert.deepEqual(
    parseQuick('每周三、五晚上7点到9点 集训 到11月30日', TODAY).tokens.map((t) => t.label),
    ['从明天起', '19:00–21:00', '每周三、五，到 11月30日'],
  )
  assert.deepEqual(
    parseQuick('10月20日到22日 省赛', TODAY).tokens.map((t) => t.label),
    ['10月20日 周二 – 10月22日 周四 · 3 天'],
  )
})

test('说不准的起止', () => {
  // 下个月哪天开始都行，大概两周（前后各留四分之一：10–18 天）；最早结束不早于最晚开始
  const got = check('下个月找个时间开始复习线代 大概两周', {
    title: '复习线代',
    allDay: true,
    startAt: '2026-11-01T00:00:00',
    startLatestAt: '2026-11-30T00:00:00',
    endMode: 'RANGE',
    endAt: '2026-11-30T00:00:00',
    endLatestAt: '2026-12-17T00:00:00',
  })
  assert.deepEqual(
    got.tokens.map((t) => t.label),
    ['11月1日–11月30日 之间开始', '预计 11月30日–12月17日 结束'],
  )
  check('论文初稿 月底前完成', {
    title: '论文初稿',
    startAt: '2026-10-15T00:00:00',
    startLatestAt: null,
    endMode: 'RANGE',
    endAt: '2026-10-21T00:00:00',
    endLatestAt: '2026-10-31T00:00:00',
  })
  check('3到5天 写代码', { title: '写代码', endMode: 'RANGE', endAt: '2026-10-17T00:00:00', endLatestAt: '2026-10-19T00:00:00' })
  check('找队友 找到为止', { title: '找队友', endMode: 'OPEN', endAt: null, endLatestAt: null, startLatestAt: null })
  // 只说了什么时候开始：结束先算没定
  check('下周开始准备材料', {
    title: '准备材料',
    startAt: '2026-10-19T00:00:00',
    startLatestAt: '2026-10-25T00:00:00',
    endMode: 'OPEN',
    endAt: null,
  })
  // 确定的日程不带这些
  check('明天下午3点到5点 队伍开会', { startLatestAt: null, endMode: 'FIXED', endLatestAt: null })
})

test('说不准的日程不重复', () => {
  const got = check('每天 大概两周 背单词', { title: '背单词', repeatType: 'NONE', endMode: 'RANGE' })
  assert.ok(got.tokens.some((t) => t.kind === 'warn' && t.label.includes('说不准')))
})

test('只有时间没有标题', () => {
  check('明天下午3点', { title: '', startAt: '2026-10-16T15:00:00' })
})

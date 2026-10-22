<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import type { CalendarApi, CalendarOptions, DatesSetArg, DayCellMountArg, EventClickArg, EventInput } from '@fullcalendar/core'
import EventDialog from '@/components/EventDialog.vue'
import QuickAdd from '@/components/QuickAdd.vue'
import SemesterBand from '@/components/home/SemesterBand.vue'
import { getCalendar } from '@/api/calendar'
import type { CalendarEvent, EventForm, Semester } from '@/types/api'
import { addDays, fmtDate, parse, toDateStr } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 主页左栏：赛程日历（周 / 月）+ 学期赛程带。上面可以一句话添加个人日程；改已有的日程到完整的日历页。
 * <p>
 * 月视图只画记号，不写字：比赛期间是细线（参加的实线、关注的虚线），截止是红色菱形，队伍节点是黑色菱形，个人日程是小圆点，
 * 和别的安排撞了的记号带红框。鼠标放到某一天上（手机上点一下）才弹出当天的详情，点详情里的条目跳到比赛或队伍。
 * 周视图格子宽，照旧写出标题。
 */
/** wide：主页把日历栏加宽了（默认占 1/3） */
const props = defineProps<{ semester: Semester; today: string; wide?: boolean }>()
/** changed：加了日程，主页顶部的本周可投入时间、学期赛程带要跟着变；toggleWide：点了「加宽 / 收窄」 */
const emit = defineEmits<{ changed: []; toggleWide: [] }>()

const router = useRouter()
const raw = ref<CalendarEvent[]>([])
const view = ref<'dayGridMonth' | 'dayGridWeek'>('dayGridMonth')
const calendarRef = ref<{ getApi: () => CalendarApi } | null>(null)
const shown = ref<{ from: string; to: string } | null>(null)
const dialogOpen = ref(false)
const preset = ref<Partial<EventForm> | null>(null)

type Kind = 'joined' | 'followed' | 'deadline' | 'team' | 'personal'

function kindOf(e: CalendarEvent): Kind {
  if (e.layer === 'TEAM') return 'team'
  if (e.layer === 'PERSONAL') return 'personal'
  if (e.deadline || !e.end) return 'deadline'
  return e.joined ? 'joined' : 'followed'
}

function toInput(e: CalendarEvent): EventInput {
  const kind = kindOf(e)
  const start = e.allDay ? e.start.slice(0, 10) : e.start
  // 全天日程：FullCalendar 的结束日期不含当天，要往后挪一天；菱形和圆点只画开始那天
  const end =
    kind === 'joined' || kind === 'followed'
      ? e.end
        ? e.allDay
          ? toDateStr(addDays(parse(e.end), 1))
          : e.end
        : undefined
      : undefined
  return {
    id: e.id,
    title: e.layer === 'PERSONAL' ? e.title : `${e.layerName} ${e.title}`,
    start,
    end,
    allDay: kind === 'joined' || kind === 'followed' ? e.allDay || !!end : true,
    classNames: [`jd-ev`, `jd-${kind}`, e.conflictWith ? 'jd-conflict' : '', e.done ? 'jd-done' : ''].filter(Boolean),
    extendedProps: { raw: e, kind },
  }
}

/** 月视图只画记号 */
const compact = computed(() => view.value === 'dayGridMonth')
/** 一个格子里最多画几个记号，多的写 +n */
const MARKS_SHOWN = 4
const POP_WIDTH = 260

const isSpan = (k: Kind) => k === 'joined' || k === 'followed'

// 月视图交给 FullCalendar 的只有比赛期间（画成细线）；菱形和圆点自己画在日期旁边
const events = computed(() =>
  raw.value.filter((e) => !compact.value || isSpan(kindOf(e))).map(toInput),
)

/** 一天一天的记号：截止、队伍节点、个人日程，只画开始那天 */
const singles = computed(() => {
  const byDay = new Map<string, CalendarEvent[]>()
  for (const e of raw.value) {
    if (isSpan(kindOf(e))) continue
    const day = e.start.slice(0, 10)
    const list = byDay.get(day)
    if (list) list.push(e)
    else byDay.set(day, [e])
  }
  return byDay
})

const marksOf = (date: Date) => singles.value.get(toDateStr(date)) ?? []

/** 这一天的全部安排：当天的记号，加上覆盖这一天的比赛期间 */
function itemsOn(day: string): CalendarEvent[] {
  const spans = raw.value.filter(
    (e) => isSpan(kindOf(e)) && e.start.slice(0, 10) <= day && (e.end ?? e.start).slice(0, 10) >= day,
  )
  return [...(singles.value.get(day) ?? []), ...spans]
}

function itemTitle(e: CalendarEvent): string {
  return e.layer === 'PERSONAL' ? e.title : `${e.layerName} ${e.title}`
}

function timeText(e: CalendarEvent): string {
  if (isSpan(kindOf(e))) return `${fmtDate(e.start)} 至 ${fmtDate(e.end)}` + (e.fuzzy ? '（时间未定）' : '')
  if (e.allDay) return e.fuzzy ? '时间未定' : '全天'
  const t = e.start.slice(11, 16)
  return e.end && e.end.slice(0, 10) === e.start.slice(0, 10) ? `${t}–${e.end.slice(11, 16)}` : t
}

function dayText(day: string): string {
  const d = parse(`${day}T00:00:00`)
  return `${d.getMonth() + 1}月${d.getDate()}日 周${'日一二三四五六'[d.getDay()]}`
}

// ---------- 当天详情的浮层 ----------

const pop = ref<{ day: string; items: CalendarEvent[]; x: number; y: number } | null>(null)
let showTimer: ReturnType<typeof setTimeout> | undefined
let hideTimer: ReturnType<typeof setTimeout> | undefined

function openPop(cell: HTMLElement, day: string) {
  clearTimeout(hideTimer)
  const items = itemsOn(day)
  if (!compact.value || items.length === 0) {
    pop.value = null
    return
  }
  const r = cell.getBoundingClientRect()
  const vw = window.innerWidth
  let x: number
  let y = r.top
  if (r.right + 8 + POP_WIDTH <= vw - 8) x = r.right + 8
  else if (r.left - 8 - POP_WIDTH >= 8) x = r.left - 8 - POP_WIDTH
  else {
    // 左右都放不下（手机上）：放在格子下面
    x = Math.min(Math.max(8, r.left + r.width / 2 - POP_WIDTH / 2), vw - POP_WIDTH - 8)
    y = r.bottom + 6
  }
  pop.value = { day, items, x, y: Math.max(8, Math.min(y, window.innerHeight - 60 - items.length * 46)) }
}

function hidePopSoon() {
  clearTimeout(showTimer)
  hideTimer = setTimeout(() => (pop.value = null), 180)
}

function keepPop() {
  clearTimeout(hideTimer)
}

function onDayCellMount(arg: DayCellMountArg) {
  const day = toDateStr(arg.date)
  const cell = arg.el
  cell.addEventListener('mouseenter', () => {
    clearTimeout(hideTimer)
    clearTimeout(showTimer)
    showTimer = setTimeout(() => openPop(cell, day), 120)
  })
  cell.addEventListener('mouseleave', hidePopSoon)
  // 手机上没有悬停：点一下格子打开；点的是细线（比赛期间）就照旧跳到比赛页
  cell.addEventListener('click', (ev) => {
    if (!(ev.target as HTMLElement).closest('.fc-event')) openPop(cell, day)
  })
}

function closePop(ev: Event) {
  if (pop.value && !(ev.target as HTMLElement).closest?.('.day-pop, .fc-daygrid-day')) pop.value = null
}

function closePopNow() {
  pop.value = null
}

onMounted(() => {
  document.addEventListener('click', closePop)
  window.addEventListener('scroll', closePopNow, true)
})
onBeforeUnmount(() => {
  clearTimeout(showTimer)
  clearTimeout(hideTimer)
  document.removeEventListener('click', closePop)
  window.removeEventListener('scroll', closePopNow, true)
})

async function load() {
  if (!shown.value) return
  try {
    raw.value = (await getCalendar(shown.value.from, shown.value.to)).events
  } catch (e) {
    showError(e)
  }
}

function onDatesSet(info: DatesSetArg) {
  shown.value = { from: toDateStr(info.start), to: toDateStr(addDays(info.end, -1)) }
  load()
}

function onAdded() {
  load()
  emit('changed')
}

function adjust(form: EventForm) {
  preset.value = form
  dialogOpen.value = true
}

function go(e: CalendarEvent) {
  pop.value = null
  if (e.refType === 'COMPETITION' && e.refId) router.push({ name: 'competition', params: { id: e.refId } })
  else if (e.refType === 'TEAM' && e.refId) router.push({ name: 'team', params: { id: e.refId } })
  else router.push({ name: 'calendar' })
}

function onEventClick(info: EventClickArg) {
  go(info.event.extendedProps.raw as CalendarEvent)
}

function switchView(v: 'dayGridMonth' | 'dayGridWeek') {
  view.value = v
  pop.value = null
  calendarRef.value?.getApi().changeView(v)
}

// 栏宽变了，FullCalendar 要重新量一下格子
watch(
  () => props.wide,
  () => nextTick(() => calendarRef.value?.getApi().updateSize()),
)

const options = computed<CalendarOptions>(() => ({
  plugins: [dayGridPlugin],
  locale: zhCnLocale,
  // 周 / 月切换走 changeView，这里只管第一次显示
  initialView: 'dayGridMonth',
  headerToolbar: { left: 'title', center: '', right: 'prev,today,next' },
  height: 'auto',
  fixedWeekCount: false,
  dayMaxEvents: compact.value ? 4 : 3,
  displayEventTime: false,
  events: events.value,
  datesSet: onDatesSet,
  eventClick: onEventClick,
  dayCellDidMount: onDayCellMount,
}))
</script>

<template>
  <section class="panel schedule">
    <div class="panel-head">
      <div>
        <div class="kicker">SCHEDULE <span class="zh">/ 赛程</span></div>
      </div>
      <div class="tools">
        <el-radio-group :model-value="view" size="small" @update:model-value="(v) => switchView(v as 'dayGridMonth' | 'dayGridWeek')">
          <el-radio-button value="dayGridWeek">周</el-radio-button>
          <el-radio-button value="dayGridMonth">月</el-radio-button>
        </el-radio-group>
        <button type="button" class="more width-toggle" @click="emit('toggleWide')">{{ wide ? '收窄' : '加宽' }}</button>
        <router-link to="/calendar" class="more">完整日历 →</router-link>
      </div>
    </div>

    <QuickAdd :today="today" @added="onAdded" @adjust="adjust" />

    <FullCalendar ref="calendarRef" :options="options" class="cal" :class="{ compact }">
      <template #eventContent="arg">
        <template v-if="!compact">
          <span class="ev-mark" aria-hidden="true" />
          <span class="ev-title" :title="arg.event.title">{{ arg.event.title }}</span>
        </template>
      </template>
      <template #dayCellContent="arg">
        <span v-if="compact && marksOf(arg.date).length" class="marks" aria-hidden="true">
          <i
            v-for="e in marksOf(arg.date).slice(0, MARKS_SHOWN)"
            :key="e.id"
            class="mark"
            :class="[`mk-${kindOf(e)}`, { conflict: !!e.conflictWith, done: !!e.done }]"
          />
          <span v-if="marksOf(arg.date).length > MARKS_SHOWN" class="more-marks">+{{ marksOf(arg.date).length - MARKS_SHOWN }}</span>
        </span>
        <span>{{ arg.dayNumberText }}</span>
      </template>
    </FullCalendar>

    <Teleport to="body">
      <div
        v-if="pop"
        class="day-pop"
        :style="{ left: `${pop.x}px`, top: `${pop.y}px`, width: `${POP_WIDTH}px` }"
        role="dialog"
        :aria-label="`${dayText(pop.day)}的安排`"
        @mouseenter="keepPop"
        @mouseleave="hidePopSoon"
      >
        <div class="pop-day">{{ dayText(pop.day) }}</div>
        <button v-for="e in pop.items" :key="e.id" type="button" class="pop-item" @click="go(e)">
          <i class="mark" :class="[`mk-${kindOf(e)}`, { done: !!e.done }]" aria-hidden="true" />
          <span class="pop-text">
            <b :class="{ 'is-done': !!e.done }">{{ itemTitle(e) }}</b>
            <span class="pop-time">{{ timeText(e) }}</span>
            <span v-if="e.conflictWith" class="pop-conflict">和{{ e.conflictWith }}撞了</span>
          </span>
        </button>
        <router-link to="/calendar" class="pop-more" @click="pop = null">在完整日历里看 →</router-link>
      </div>
    </Teleport>

    <hr class="hairline" />
    <SemesterBand :semester="semester" :today="today" />

    <EventDialog v-model="dialogOpen" :event-id="null" :preset="preset" @saved="onAdded" />
  </section>
</template>

<style scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.more {
  font-size: 12px;
  color: var(--jd-muted);
  text-decoration: none;
}

.more:hover {
  color: var(--jd-accent);
}

.width-toggle {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

/* 窄屏时日历本来就占满一行，不用加宽 */
@media (max-width: 1200px) {
  .width-toggle {
    display: none;
  }
}

.cal {
  font-size: 12px;
}

.cal :deep(.fc-toolbar-title) {
  font-size: 15px;
  font-weight: 700;
}

.cal :deep(.fc-daygrid-day-number) {
  font-size: 12px;
  color: var(--jd-ink-2);
  text-decoration: none;
}

.cal :deep(.fc-col-header-cell-cushion) {
  font-size: 11px;
  color: var(--jd-muted);
  text-decoration: none;
}

/* 事件：横条 / 菱形 / 圆点 */
.cal :deep(.jd-ev) {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  padding: 0 3px;
  border-radius: 0;
  border: 1px solid transparent;
  background: transparent;
  color: var(--jd-ink);
  cursor: pointer;
}

/* FullCalendar 默认把事件文字设成白色，浅底的菱形、虚线框会看不见，改成跟着外层的颜色 */
.cal :deep(.jd-ev .fc-event-main),
.cal :deep(.jd-ev.fc-h-event .fc-event-main) {
  color: inherit;
}

.cal :deep(.jd-joined) {
  background: var(--jd-ink);
  border-color: var(--jd-ink);
  color: var(--jd-paper);
}

.cal :deep(.jd-followed) {
  border: 1px dashed var(--jd-line-strong);
  color: var(--jd-ink-2);
}

.cal :deep(.jd-conflict) {
  border-color: var(--jd-red) !important;
}

.cal :deep(.jd-done) {
  opacity: 0.5;
  text-decoration: line-through;
}

.cal :deep(.ev-mark) {
  flex: none;
  display: none;
  width: 7px;
  height: 7px;
}

.cal :deep(.jd-deadline .ev-mark),
.cal :deep(.jd-team .ev-mark) {
  display: inline-block;
  transform: rotate(45deg);
}

.cal :deep(.jd-deadline .ev-mark) {
  background: var(--jd-red);
}

.cal :deep(.jd-deadline) {
  color: var(--jd-red);
}

.cal :deep(.jd-team .ev-mark) {
  background: var(--jd-ink);
}

.cal :deep(.jd-personal .ev-mark) {
  display: inline-block;
  border: 1px solid var(--jd-ink-2);
  border-radius: 50%;
}

.cal :deep(.ev-title) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 月视图：只画记号 ---------- */

.cal.compact :deep(.fc-daygrid-day) {
  cursor: default;
}

.cal.compact :deep(.fc-daygrid-day:hover) {
  background: var(--jd-overlay);
}

.cal.compact :deep(.fc-daygrid-day-number) {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 比赛期间：参加的是实线，关注的是虚线 */
.cal.compact :deep(.jd-ev.jd-joined),
.cal.compact :deep(.jd-ev.jd-followed) {
  height: 0;
  min-height: 0;
  margin-top: 3px;
  padding: 0;
  border: 0;
  background: none;
}

.cal.compact :deep(.jd-ev.jd-joined) {
  border-top: 3px solid var(--jd-ink);
}

.cal.compact :deep(.jd-ev.jd-followed) {
  border-top: 2px dashed var(--jd-line-strong);
}

.cal.compact :deep(.jd-ev.jd-conflict) {
  border-top-color: var(--jd-red) !important;
}

.marks {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.more-marks {
  font-size: 10px;
  color: var(--jd-muted);
}

/* 记号：截止红菱形、队伍节点黑菱形、个人日程空心小圆点；格子里和浮层里共用 */
.mark {
  flex: none;
  display: inline-block;
  width: 7px;
  height: 7px;
}

.mark.mk-deadline,
.mark.mk-team {
  transform: rotate(45deg) scale(0.9);
}

.mark.mk-deadline {
  background: var(--jd-red);
}

.mark.mk-team {
  background: var(--jd-ink);
}

.mark.mk-personal {
  border: 1.5px solid var(--jd-ink-2);
  border-radius: 50%;
}

.mark.mk-joined {
  width: 12px;
  height: 3px;
  background: var(--jd-ink);
}

.mark.mk-followed {
  width: 12px;
  height: 0;
  border-top: 2px dashed var(--jd-line-strong);
}

.mark.conflict {
  outline: 1.5px solid var(--jd-red);
  outline-offset: 1px;
}

.mark.done {
  opacity: 0.4;
}

/* ---------- 当天详情的浮层 ---------- */

.day-pop {
  position: fixed;
  z-index: 3000;
  padding: 10px 12px 8px;
  border: 1px solid var(--jd-line);
  background: var(--jd-panel);
  box-shadow: 0 6px 18px rgb(18 19 16 / 12%);
  font-size: 12px;
  color: var(--jd-ink);
}

.pop-day {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 700;
}

.pop-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 6px 0;
  border: 0;
  border-top: 1px solid var(--jd-line-lighter);
  background: none;
  font: inherit;
  text-align: left;
  color: inherit;
  cursor: pointer;
}

.pop-item .mark {
  margin-top: 5px;
}

.pop-item:hover b {
  color: var(--jd-accent);
}

.pop-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.pop-text b {
  font-weight: 700;
}

.pop-text b.is-done {
  text-decoration: line-through;
  color: var(--jd-muted);
}

.pop-time {
  color: var(--jd-muted);
}

.pop-conflict {
  color: var(--jd-red);
}

.pop-more {
  display: block;
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px solid var(--jd-line-lighter);
  color: var(--jd-muted);
  text-decoration: none;
}

.pop-more:hover {
  color: var(--jd-accent);
}
</style>

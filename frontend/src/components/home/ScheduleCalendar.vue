<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import type { CalendarApi, CalendarOptions, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core'
import SemesterBand from '@/components/home/SemesterBand.vue'
import { getCalendar } from '@/api/calendar'
import type { CalendarEvent, Semester } from '@/types/api'
import { addDays, parse, toDateStr } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 主页左栏：赛程日历（周 / 月）+ 学期赛程带。
 * 比赛期间画成横条（参加的实心、关注的虚线框），截止是红色菱形，队伍节点是黑色菱形，个人日程是小圆点。
 * 只看不改：新建个人日程到完整的日历页。
 */
defineProps<{ semester: Semester; today: string }>()

const router = useRouter()
const raw = ref<CalendarEvent[]>([])
const view = ref<'dayGridMonth' | 'dayGridWeek'>('dayGridMonth')
const calendarRef = ref<{ getApi: () => CalendarApi } | null>(null)

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

const events = computed(() => raw.value.map(toInput))

async function onDatesSet(info: DatesSetArg) {
  try {
    raw.value = (await getCalendar(toDateStr(info.start), toDateStr(addDays(info.end, -1)))).events
  } catch (e) {
    showError(e)
  }
}

function onEventClick(info: EventClickArg) {
  const e = info.event.extendedProps.raw as CalendarEvent
  if (e.refType === 'COMPETITION' && e.refId) router.push({ name: 'competition', params: { id: e.refId } })
  else if (e.refType === 'TEAM' && e.refId) router.push({ name: 'team', params: { id: e.refId } })
  else router.push({ name: 'calendar' })
}

function switchView(v: 'dayGridMonth' | 'dayGridWeek') {
  view.value = v
  calendarRef.value?.getApi().changeView(v)
}

const options = computed<CalendarOptions>(() => ({
  plugins: [dayGridPlugin],
  locale: zhCnLocale,
  // 周 / 月切换走 changeView，这里只管第一次显示
  initialView: 'dayGridMonth',
  headerToolbar: { left: 'title', center: '', right: 'prev,today,next' },
  height: 'auto',
  fixedWeekCount: false,
  dayMaxEvents: 3,
  displayEventTime: false,
  events: events.value,
  datesSet: onDatesSet,
  eventClick: onEventClick,
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
        <router-link to="/calendar" class="more">完整日历 →</router-link>
      </div>
    </div>

    <FullCalendar ref="calendarRef" :options="options" class="cal">
      <template #eventContent="arg">
        <span class="ev-mark" aria-hidden="true" />
        <span class="ev-title">{{ arg.event.title }}</span>
      </template>
    </FullCalendar>

    <hr class="hairline" />
    <SemesterBand :semester="semester" :today="today" />
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
</style>

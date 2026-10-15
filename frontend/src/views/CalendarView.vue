<script setup lang="ts">
import { computed, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import listPlugin from '@fullcalendar/list'
import interactionPlugin from '@fullcalendar/interaction'
import zhCnLocale from '@fullcalendar/core/locales/zh-cn'
import type { CalendarOptions, DateSelectArg, DatesSetArg, EventClickArg, EventInput } from '@fullcalendar/core'
import CalendarFeedDialog from '@/components/CalendarFeedDialog.vue'
import EventDialog from '@/components/EventDialog.vue'
import QuickAdd from '@/components/QuickAdd.vue'
import { getCalendar } from '@/api/calendar'
import type { CalendarConflict, CalendarEvent, EventForm } from '@/types/api'
import { addDays, fmtDate, parse, toDateStr, toLocalIso } from '@/utils/time'
import { showError } from '@/utils/feedback'

const router = useRouter()

const raw = ref<CalendarEvent[]>([])
const conflicts = ref<CalendarConflict[]>([])
const loading = ref(false)
const range = ref<{ from: string; to: string } | null>(null)

const dialogOpen = ref(false)
const feedOpen = ref(false)
const editingId = ref<number | null>(null)
const editingDate = ref<string | null>(null)
const preset = ref<Partial<EventForm> | null>(null)

// ---------- 图层 ----------

// 竞搭配色：官方暖灰，个人石板灰，队伍依次是近黑、金棕、橄榄、褐、深灰
const OFFICIAL_COLOR = '#8f8a80'
const PERSONAL_COLOR = '#536166'
const TEAM_COLORS = ['#121310', '#a67d48', '#565c46', '#7a5c3e', '#3b3b35']
const HIDDEN_KEY = 'tm_calendar_hidden'

function loadHidden(): Set<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(HIDDEN_KEY) ?? '[]') as string[])
  } catch {
    return new Set()
  }
}

const hidden = ref<Set<string>>(loadHidden())

function toggle(key: string) {
  const next = new Set(hidden.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  hidden.value = next
  try {
    localStorage.setItem(HIDDEN_KEY, JSON.stringify([...next]))
  } catch {
    // 存不了就只在这次有效
  }
}

/** 图层列表：比赛官方合成一个开关，每支队伍一个，个人一个 */
const layers = computed(() => {
  const teams = new Map<string, string>()
  for (const e of raw.value) if (e.layer === 'TEAM') teams.set(e.layerKey, e.layerName)
  return [
    { key: 'official', name: '比赛官方', color: OFFICIAL_COLOR },
    ...[...teams.entries()].sort().map(([key, name], i) => ({ key, name, color: TEAM_COLORS[i % TEAM_COLORS.length]! })),
    { key: 'personal', name: '个人', color: PERSONAL_COLOR },
  ]
})

const colorOf = (key: string) => layers.value.find((l) => l.key === key)?.color ?? OFFICIAL_COLOR
const toggleKey = (e: CalendarEvent) => (e.layer === 'OFFICIAL' ? 'official' : e.layerKey)

// ---------- 事件 ----------

function toInput(e: CalendarEvent): EventInput {
  const color = colorOf(toggleKey(e))
  const title =
    e.layer === 'OFFICIAL' ? `${e.layerName} · ${e.title}` : e.layer === 'TEAM' ? `${e.layerName}：${e.title}` : e.title
  // 全天日程：FullCalendar 的结束日期是"不含"的，要往后挪一天
  const start = e.allDay ? e.start.slice(0, 10) : e.start
  const end = e.end ? (e.allDay ? toDateStr(addDays(parse(e.end), 1)) : e.end) : undefined
  return {
    id: e.id,
    title,
    start,
    end,
    allDay: e.allDay,
    backgroundColor: color,
    borderColor: e.conflictWith ? '#b0442d' : color,
    classNames: [e.fuzzy ? 'is-fuzzy' : '', e.conflictWith ? 'is-conflict' : '', e.done ? 'is-done' : ''].filter(Boolean),
    extendedProps: { raw: e },
  }
}

const events = computed(() => raw.value.filter((e) => !hidden.value.has(toggleKey(e))).map(toInput))

async function load() {
  if (!range.value) return
  loading.value = true
  try {
    const data = await getCalendar(range.value.from, range.value.to)
    raw.value = data.events
    conflicts.value = data.conflicts
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function onDatesSet(info: DatesSetArg) {
  range.value = { from: toDateStr(info.start), to: toDateStr(addDays(info.end, -1)) }
  load()
}

function onEventClick(info: EventClickArg) {
  const e = info.event.extendedProps.raw as CalendarEvent
  if (e.refType === 'COMPETITION' && e.refId) router.push({ name: 'competition', params: { id: e.refId } })
  else if (e.refType === 'TEAM' && e.refId) router.push({ name: 'team', params: { id: e.refId } })
  else if (e.refType === 'EVENT' && e.refId) {
    editingId.value = e.refId
    editingDate.value = e.occurrenceDate
    preset.value = null
    dialogOpen.value = true
  }
}

/** 在日历上拖选一段时间 → 新建日程 */
function onSelect(info: DateSelectArg) {
  const lastDay = addDays(info.end, -1)
  preset.value = info.allDay
    ? {
        startAt: `${info.startStr}T00:00:00`,
        endAt: toDateStr(lastDay) === info.startStr ? null : `${toDateStr(lastDay)}T00:00:00`,
        allDay: true,
      }
    : { startAt: toLocalIso(info.start), endAt: toLocalIso(info.end), allDay: false }
  openNew()
}

function openNew() {
  editingId.value = null
  editingDate.value = null
  dialogOpen.value = true
}

function newEvent() {
  preset.value = null
  openNew()
}

/** 一句话添加认得不对，打开完整表单改 */
function adjustQuick(form: EventForm) {
  preset.value = form
  openNew()
}

const options = computed<CalendarOptions>(() => ({
  plugins: [dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin],
  locale: zhCnLocale,
  initialView: 'dayGridMonth',
  firstDay: 1,
  height: 'auto',
  headerToolbar: { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,listMonth' },
  buttonText: { listMonth: '列表' },
  dayMaxEvents: 4,
  selectable: true,
  nowIndicator: true,
  eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
  events: events.value,
  datesSet: onDatesSet,
  eventClick: onEventClick,
  select: onSelect,
}))
</script>

<template>
  <div class="page">
    <PageHeader en="SCHEDULE" zh="日历" />
    <div class="toolbar">
      <div class="layers" role="group" aria-label="图层">
        <el-check-tag
          v-for="l in layers"
          :key="l.key"
          :checked="!hidden.has(l.key)"
          class="layer"
          @change="toggle(l.key)"
        >
          <span class="dot" :style="{ background: l.color }" />
          {{ l.name }}
        </el-check-tag>
      </div>
      <el-button @click="feedOpen = true">订阅到手机日历</el-button>
      <el-button type="primary" :icon="Plus" @click="newEvent">新建日程</el-button>
    </div>

    <QuickAdd @added="load" @adjust="adjustQuick" />

    <el-alert v-if="conflicts.length" type="error" :closable="false" show-icon class="conflicts">
      <template #title>撞期提醒（{{ conflicts.length }}）</template>
      <div v-for="(c, i) in conflicts" :key="i" class="conflict">{{ fmtDate(c.start) }} {{ c.first }} 和 {{ c.second }} 时间重叠</div>
    </el-alert>

    <el-card v-loading="loading" shadow="never" class="calendar">
      <FullCalendar :options="options" />
    </el-card>
    <p class="legend">虚线框是时间还没定的节点（比如"成绩预计 11 月中下旬公布"），红框是撞期的。关注的比赛和参加的队伍会自动出现在这里。</p>

    <CalendarFeedDialog v-model="feedOpen" />
    <EventDialog v-model="dialogOpen" :event-id="editingId" :occurrence-date="editingDate" :preset="preset" @saved="load" />
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.layers {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.layer {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 400;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.conflicts {
  margin-bottom: 12px;
}

.conflict {
  font-size: 13px;
  margin-top: 2px;
}

.calendar {
  border-radius: 2px;
}

.legend {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 8px 0 0;
}

.calendar :deep(.is-fuzzy) {
  border-style: dashed !important;
  opacity: 0.75;
}

.calendar :deep(.is-conflict) {
  box-shadow: 0 0 0 1px var(--jd-red);
}

.calendar :deep(.is-done .fc-event-title) {
  text-decoration: line-through;
}
</style>

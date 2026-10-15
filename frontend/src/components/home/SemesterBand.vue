<script setup lang="ts">
import { computed } from 'vue'
import type { Semester, SemesterMark, WeekLoad } from '@/types/api'
import { fmtDate, parse } from '@/utils/time'

/**
 * 学期赛程带：整个学期压成一条，每个比赛一行（参加了的实心、关注的虚线框），截止日是红色菱形，
 * 自己队伍的里程碑是黑色菱形；下面一排是每周空闲，截止多、空闲少的那周标红并给出提醒。
 */
const props = defineProps<{ semester: Semester; today: string }>()

const DAY = 86_400_000
const startT = computed(() => parse(`${props.semester.start}T00:00:00`).getTime())
const endT = computed(() => parse(`${props.semester.end}T00:00:00`).getTime() + DAY)

/** 时间 → 在条上的百分比位置（超出学期的夹到两端） */
function pct(iso: string): number {
  const t = parse(iso.length === 10 ? `${iso}T00:00:00` : iso).getTime()
  return Math.min(100, Math.max(0, ((t - startT.value) / (endT.value - startT.value)) * 100))
}

const todayPct = computed(() => pct(props.today))

const months = computed(() => {
  const out: { label: string; left: number }[] = []
  const d = parse(`${props.semester.start}T00:00:00`)
  d.setDate(1)
  while (d.getTime() < endT.value) {
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
    out.push({ label: `${d.getMonth() + 1}月`, left: pct(iso) })
    d.setMonth(d.getMonth() + 1)
  }
  return out
})

function barStyle(m: SemesterMark) {
  const left = pct(m.start)
  const right = m.end ? pct(m.end) : left
  return { left: `${left}%`, width: `max(3px, ${right - left}%)` }
}

const tip = (label: string, m: SemesterMark) =>
  `${label} · ${m.title} · ${fmtDate(m.start)}${m.end && m.end.slice(0, 10) !== m.start.slice(0, 10) ? ' 至 ' + fmtDate(m.end) : ''}`

const hasHours = computed(() => props.semester.weeks.some((w) => w.freeHours !== null))
const maxFree = computed(() => Math.max(20, ...props.semester.weeks.map((w) => w.freeHours ?? 0)))
const weekWidth = computed(() => (7 * DAY * 100) / (endT.value - startT.value))
const thisWeek = (w: WeekLoad) => {
  const s = pct(w.start)
  return todayPct.value >= s && todayPct.value < s + weekWidth.value
}

/** 今天之后第一个要提醒的周 */
const warning = computed(() => {
  const w = props.semester.weeks.find((x) => x.warn && pct(x.start) + weekWidth.value > todayPct.value)
  if (!w) return null
  const end = new Date(parse(`${w.start}T00:00:00`).getTime() + 6 * DAY)
  return `${fmtDate(w.start)}–${end.getMonth() + 1}-${String(end.getDate()).padStart(2, '0')} 有 ${w.deadlines} 个截止，这一周只有 ${w.freeHours} 小时空闲，建议把能提前的任务挪到前一周。`
})
</script>

<template>
  <div class="band">
    <div class="head kicker">SEMESTER <span class="zh">/ 学期赛程带 {{ fmtDate(semester.start, true) }} – {{ fmtDate(semester.end, true) }}</span></div>

    <div class="row axis">
      <div class="label" />
      <div class="track">
        <span v-for="m in months" :key="m.label" class="month" :style="{ left: `${m.left}%` }">{{ m.label }}</span>
        <span class="today-tag" :style="{ left: `${todayPct}%` }">今天</span>
      </div>
    </div>

    <div v-for="lane in semester.lanes" :key="lane.editionId" class="row">
      <router-link
        :to="lane.competitionId ? { name: 'competition', params: { id: lane.competitionId } } : '/'"
        class="label"
        :title="lane.joined ? '已参加' : '关注中'"
      >
        {{ lane.label }}
      </router-link>
      <div class="track lane">
        <span class="today-line" :style="{ left: `${todayPct}%` }" />
        <template v-for="(m, i) in lane.marks" :key="i">
          <span
            v-if="m.end && !m.deadline"
            class="bar"
            :class="{ joined: lane.joined, fuzzy: m.fuzzy }"
            :style="barStyle(m)"
            :title="tip(lane.label, m)"
          />
          <span
            v-else
            class="diamond"
            :class="{ deadline: m.deadline && !m.team, team: m.team }"
            :style="{ left: `${pct(m.start)}%` }"
            :title="tip(lane.label, m)"
          />
        </template>
      </div>
    </div>
    <p v-if="semester.lanes.length === 0" class="empty">这学期还没有比赛节点。关注或参加比赛后会画在这里。</p>

    <div class="row weeks">
      <div class="label">每周空闲</div>
      <div class="track">
        <template v-if="hasHours">
          <span
            v-for="w in semester.weeks"
            :key="w.start"
            class="week"
            :class="{ warn: w.warn, now: thisWeek(w) }"
            :style="{
              left: `${pct(w.start)}%`,
              width: `calc(${weekWidth}% - 2px)`,
              height: `${Math.max(2, ((w.freeHours ?? 0) / maxFree) * 100)}%`,
            }"
            :title="`${fmtDate(w.start)} 这周：空闲约 ${w.freeHours} 小时，${w.deadlines} 个截止`"
          />
        </template>
        <span v-else class="hint"><router-link to="/survey">在问卷里勾上空闲时段</router-link>后显示每周空闲</span>
      </div>
    </div>

    <p v-if="warning" class="warning"><strong>冲突预警</strong> {{ warning }}</p>

    <div class="legend kicker">
      <span><i class="lg joined" />我参加</span>
      <span><i class="lg followed" />关注中</span>
      <span><i class="lg dl" />截止</span>
      <span><i class="lg tm" />队伍节点</span>
    </div>
  </div>
</template>

<style scoped>
.band {
  font-size: 12px;
}

.head {
  margin-bottom: 8px;
}

.row {
  display: grid;
  grid-template-columns: 64px 1fr;
  align-items: center;
  min-height: 24px;
}

.label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
  color: var(--jd-ink);
  text-decoration: none;
}

.track {
  position: relative;
  height: 18px;
}

/* 上半行放"今天"标签，下半行放月份，两者不重叠 */
.axis .track {
  height: 32px;
  border-bottom: 1px solid var(--jd-line);
}

.month {
  position: absolute;
  bottom: 2px;
  padding-left: 3px;
  border-left: 1px solid var(--jd-line);
  color: var(--jd-muted);
  font-size: 11px;
}

.today-tag {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  padding: 0 4px;
  background: var(--jd-accent);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

.lane {
  border-bottom: 1px dashed var(--jd-line-lighter);
}

.today-line {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 1px;
  background: var(--jd-accent);
}

.bar {
  position: absolute;
  top: 5px;
  height: 8px;
  border: 1px dashed var(--jd-line-strong);
  background: transparent;
}

.bar.joined {
  border: 0;
  background: var(--jd-ink);
}

.bar.fuzzy {
  opacity: 0.55;
}

.diamond {
  position: absolute;
  top: 4px;
  width: 9px;
  height: 9px;
  margin-left: -4px;
  transform: rotate(45deg);
  border: 1px solid var(--jd-ink);
  background: var(--jd-panel);
}

.diamond.deadline {
  border-color: var(--jd-red);
  background: var(--jd-red);
}

.diamond.team {
  background: var(--jd-ink);
}

.empty {
  margin: 6px 0;
  color: var(--jd-muted);
}

.weeks {
  margin-top: 8px;
}

.weeks .track {
  height: 34px;
  border-bottom: 1px solid var(--jd-line);
}

.week {
  position: absolute;
  bottom: 0;
  background: var(--jd-line);
}

.week.now {
  background: var(--jd-ink);
}

.week.warn {
  background: var(--jd-red);
}

.hint {
  position: absolute;
  bottom: 6px;
  color: var(--jd-muted);
}

.warning {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-left: 2px solid var(--jd-red);
  background: var(--jd-red-soft);
  color: var(--jd-ink-2);
  line-height: 1.6;
}

.warning strong {
  margin-right: 6px;
  color: var(--jd-red);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 10px;
  font-size: 10px;
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.lg {
  display: inline-block;
  width: 14px;
  height: 7px;
}

.lg.joined {
  background: var(--jd-ink);
}

.lg.followed {
  border: 1px dashed var(--jd-line-strong);
}

.lg.dl,
.lg.tm {
  width: 7px;
  transform: rotate(45deg);
}

.lg.dl {
  background: var(--jd-red);
}

.lg.tm {
  background: var(--jd-ink);
}
</style>

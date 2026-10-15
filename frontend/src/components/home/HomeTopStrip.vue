<script setup lang="ts">
import { computed, ref } from 'vue'
import CalendarFeedDialog from '@/components/CalendarFeedDialog.vue'
import type { HomeData, Upcoming } from '@/types/api'
import { fmtDate, fmtDateTime, parse } from '@/utils/time'

/**
 * 主页顶部：一句话状态，下面一排是今天、下一个节点（进度环）、之后的两个、本周可投入时间。
 */
const props = defineProps<{ data: HomeData }>()

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const feedOpen = ref(false)

const today = computed(() => parse(`${props.data.today}T00:00:00`))
const dateText = computed(() => `${today.value.getMonth() + 1}月${today.value.getDate()}日`)
/** 学期第几周 */
const weekNo = computed(() => {
  const start = parse(`${props.data.semester.start}T00:00:00`)
  return Math.floor((today.value.getTime() - start.getTime()) / (7 * 86_400_000)) + 1
})

const linkOf = (u: Upcoming) =>
  u.refType === 'TEAM' ? { name: 'team', params: { id: u.refId } } : { name: 'competition', params: { id: u.refId } }

const whenText = (u: Upcoming) => (u.at.endsWith('00:00:00') ? fmtDate(u.at) : fmtDateTime(u.at))
const leftText = (u: Upcoming) => (u.daysLeft <= 0 ? '今天' : `还有 ${u.daysLeft} 天`)

/** C 版的一句话：今天有没有截止，下一个节点是什么 */
const sentence = computed(() => {
  const n = props.data.next
  if (!n) return '近期没有比赛节点。去比赛库关注几个比赛，这里会排出你的赛程。'
  if (n.daysLeft <= 0) {
    return n.deadline ? `今天有截止：${n.label}「${n.title}」，${n.at.slice(11, 16)} 前完成。` : `今天：${n.label}「${n.title}」。`
  }
  return `今天没有截止。你的下一个节点是${n.label}「${n.title}」，还有 ${n.daysLeft} 天。`
})

/** 进度环：周长 2πr，r = 24 */
const CIRC = 2 * Math.PI * 24
const dash = computed(() => `${((props.data.next?.progress ?? 0) / 100) * CIRC} ${CIRC}`)
</script>

<template>
  <section class="strip">
    <div class="sentence-row">
      <p class="sentence" :class="{ urgent: data.next && data.next.daysLeft <= 0 && data.next.deadline }">{{ sentence }}</p>
      <el-button size="small" @click="feedOpen = true">订阅到手机日历</el-button>
    </div>
    <CalendarFeedDialog v-model="feedOpen" />
    <div class="cells">
      <div class="cell today">
        <div class="date">{{ dateText }}</div>
        <div class="kicker">{{ WEEKDAYS[today.getDay()] }} <span class="zh">· 学期第 {{ weekNo }} 周</span></div>
      </div>

      <div v-if="data.next" class="cell next">
        <svg viewBox="0 0 56 56" class="ring" aria-hidden="true">
          <circle cx="28" cy="28" r="24" class="track" />
          <circle cx="28" cy="28" r="24" class="arc" :stroke-dasharray="dash" />
          <text x="28" y="31" text-anchor="middle" class="ring-num">{{ Math.max(data.next.daysLeft, 0) }}</text>
        </svg>
        <div class="next-text">
          <div class="kicker">NEXT <span class="zh">/ 下一个节点</span></div>
          <router-link :to="linkOf(data.next)" class="next-title">
            {{ data.next.label }} · {{ data.next.title }}
          </router-link>
          <div class="sub" :class="{ deadline: data.next.deadline && data.next.daysLeft <= 3 }">
            {{ whenText(data.next) }}{{ data.next.deadline ? ' 截止' : '' }} · {{ leftText(data.next) }}
            <template v-if="data.next.progress !== null"> · 本阶段已过 {{ data.next.progress }}%</template>
          </div>
        </div>
      </div>

      <div v-if="data.after.length" class="cell after">
        <div class="kicker">THEN <span class="zh">/ 之后</span></div>
        <router-link v-for="u in data.after" :key="u.label + u.title + u.at" :to="linkOf(u)" class="after-row">
          <span class="num">{{ fmtDate(u.at) }}</span>
          <span>{{ u.label }} {{ u.title }}</span>
        </router-link>
      </div>

      <div class="cell hours">
        <div class="kicker">THIS WEEK <span class="zh">/ 本周可投入</span></div>
        <template v-if="data.weekFreeHours !== null">
          <div class="big num">{{ data.weekFreeHours }}<small>小时</small></div>
          <div class="sub">按问卷的空闲时段扣掉日程估算 · 本周 {{ data.weekDeadlines }} 个截止</div>
        </template>
        <template v-else>
          <div class="big">—</div>
          <div class="sub"><router-link to="/survey">在问卷里勾上空闲时段</router-link>就能估算</div>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.strip {
  margin-bottom: 16px;
}

.sentence-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.sentence {
  margin: 0;
  font-size: 13px;
  color: var(--jd-ink-2);
}

.sentence.urgent {
  color: var(--jd-red);
}

.cells {
  display: grid;
  grid-template-columns: auto minmax(260px, 1.4fr) minmax(200px, 1fr) minmax(200px, 1fr);
  border-top: 1px solid var(--jd-ink);
  border-bottom: 1px solid var(--jd-line-light);
}

.cell {
  padding: 14px 20px 14px 0;
}

.cell + .cell {
  padding-left: 20px;
  border-left: 1px solid var(--jd-line-lighter);
}

.date {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  white-space: nowrap;
}

.next {
  display: flex;
  gap: 14px;
  align-items: center;
}

.ring {
  flex: none;
  width: 56px;
  height: 56px;
  transform: rotate(-90deg);
}

.track {
  fill: none;
  stroke: var(--jd-line-light);
  stroke-width: 3;
}

.arc {
  fill: none;
  stroke: var(--jd-accent);
  stroke-width: 3;
}

.ring-num {
  transform: rotate(90deg);
  transform-origin: 28px 28px;
  font-size: 17px;
  font-weight: 700;
  fill: var(--jd-ink);
}

.next-text {
  min-width: 0;
}

.next-title {
  display: block;
  margin: 2px 0;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none;
}

.sub {
  font-size: 12px;
  color: var(--jd-muted);
}

.after-row {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  font-size: 13px;
  text-decoration: none;
}

.after-row .num {
  color: var(--jd-muted);
  white-space: nowrap;
}

.big {
  margin: 2px 0;
  font-size: 26px;
  font-weight: 700;
}

.big small {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--jd-muted);
}

@media (max-width: 1100px) {
  .cells {
    grid-template-columns: 1fr 1fr;
  }

  .cell:nth-child(3) {
    padding-left: 0;
    border-left: 0;
  }

  .cell:nth-child(n + 3) {
    border-top: 1px solid var(--jd-line-lighter);
  }
}

@media (max-width: 640px) {
  .cells {
    grid-template-columns: 1fr;
  }

  .cell + .cell {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid var(--jd-line-lighter);
  }
}
</style>

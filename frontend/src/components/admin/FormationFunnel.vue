<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardFunnel } from '@/types/api'

/**
 * 一届比赛的组队漏斗：关注或在队里 → 填了问卷 → 申请或邀请过 → 进了队 → 队伍组好了。
 * 每一步写人数和占第一步的比例，两步之间写留下了多少；掉得最多的一步标红，并写上看完该做什么。
 */
const props = defineProps<{ funnel: DashboardFunnel }>()

/** 每一步掉人多的时候，管理员可以做的事 */
const STEPS = [
  { key: 'people', label: '关注或已在队里', advice: '' },
  { key: 'surveyed', label: '填了问卷', advice: '很多人没填问卷，推荐打分没有依据：发站内信提醒填一下' },
  { key: 'engaged', label: '申请或邀请过（含建队）', advice: '填了问卷却没动作：推一下推荐和批量撮合建议' },
  { key: 'joined', label: '进了队', advice: '申请和邀请大多没成：催队长处理积压的申请，看看推荐是否靠谱' },
  { key: 'formed', label: '队伍组好了', advice: '进了队但队伍还缺人：看下面的技能缺口，帮队伍招人' },
] as const

const rows = computed(() => {
  const f = props.funnel
  const base = f.people || 1
  return STEPS.map((s, i) => {
    const value = f[s.key]
    const prev = i === 0 ? null : f[STEPS[i - 1]!.key]
    // 填问卷和申请过不是严格的先后关系（没填问卷也能申请），比上一步还多时不写留下的比例
    const kept = prev === null || prev === 0 || value > prev ? null : value / prev
    return { ...s, value, share: value / base, kept }
  })
})

/** 掉得最多的一步（留下的比例最低），至少掉了两成才提醒 */
const worst = computed(() => {
  let pick: (typeof rows.value)[number] | null = null
  for (const r of rows.value) {
    if (r.kept !== null && r.kept < 0.8 && (pick === null || r.kept < pick.kept!)) pick = r
  }
  return pick
})

const pct = (x: number) => `${Math.round(x * 100)}%`
</script>

<template>
  <div class="funnel">
    <p v-if="funnel.people === 0" class="empty">这一届还没有人关注，也没有队伍。</p>
    <ol v-else class="steps">
      <li v-for="r in rows" :key="r.key" class="step" :class="{ worst: worst?.key === r.key }">
        <div v-if="r.kept !== null" class="kept">↓ 留下 {{ pct(r.kept) }}</div>
        <div class="row">
          <span class="label">{{ r.label }}</span>
          <span class="bar-wrap">
            <span class="bar" :style="{ width: `${Math.max(r.share * 100, r.value > 0 ? 2 : 0)}%` }" />
          </span>
          <span class="value num">{{ r.value }}<small> 人 · {{ pct(r.share) }}</small></span>
        </div>
      </li>
    </ol>
    <p v-if="worst" class="advice"><b>卡在「{{ worst.label }}」这一步。</b>{{ worst.advice }}</p>
  </div>
</template>

<style scoped>
.steps {
  margin: 0;
  padding: 0;
  list-style: none;
}

.kept {
  padding: 1px 0 1px 152px;
  font-size: 11px;
  color: var(--jd-muted);
}

.row {
  display: grid;
  grid-template-columns: 144px 1fr 92px;
  align-items: center;
  gap: 8px;
}

.label {
  font-size: 12px;
  color: var(--jd-ink-2);
  text-align: right;
}

.bar-wrap {
  height: 14px;
  background: var(--jd-field);
}

.bar {
  display: block;
  height: 100%;
  background: var(--jd-accent);
}

.value {
  font-size: 13px;
  font-weight: 700;
}

.value small {
  font-weight: 400;
  color: var(--jd-muted);
}

.step.worst .kept {
  color: var(--jd-red);
  font-weight: 700;
}

.step.worst .bar {
  background: var(--jd-red);
}

.advice {
  margin: 12px 0 0;
  padding: 8px 12px;
  border-left: 2px solid var(--jd-red);
  background: var(--jd-red-soft);
  font-size: 12px;
  line-height: 1.6;
}

.empty {
  margin: 24px 0;
  font-size: 13px;
  color: var(--jd-muted);
  text-align: center;
}

@media (max-width: 560px) {
  .row {
    grid-template-columns: 92px 1fr 76px;
  }

  .kept {
    padding-left: 100px;
  }
}
</style>

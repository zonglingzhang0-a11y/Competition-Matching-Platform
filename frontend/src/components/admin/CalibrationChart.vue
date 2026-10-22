<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardCalibration } from '@/types/api'

/**
 * 推荐准不准：处理完的申请和邀请按发出时的匹配分分段，每段画"成了的比例"。
 * 分越高、成的比例越高，说明推荐靠谱；高分段反而不比低分段高，就该去「匹配配置」调权重。
 * 一段里不到 MIN_SAMPLE 条的标"样本太少"，下结论时不算它。
 */
const props = defineProps<{ data: DashboardCalibration }>()

const MIN_SAMPLE = 3

const rows = computed(() =>
  [...props.data.buckets].reverse().map((b) => ({
    ...b,
    rate: b.total ? b.accepted / b.total : 0,
    enough: b.total >= MIN_SAMPLE,
  })),
)

const total = computed(() => props.data.buckets.reduce((s, b) => s + b.total, 0))

/** 60 分以上和 60 分以下比一比；两边都有够数的样本才下结论 */
const verdict = computed(() => {
  const high = props.data.buckets.filter((b) => b.from >= 60)
  const low = props.data.buckets.filter((b) => b.from < 60)
  const sum = (list: typeof high) => list.reduce((s, b) => ({ total: s.total + b.total, ok: s.ok + b.accepted }), { total: 0, ok: 0 })
  const h = sum(high)
  const l = sum(low)
  if (h.total < MIN_SAMPLE || l.total < MIN_SAMPLE) return null
  const hr = h.ok / h.total
  const lr = l.ok / l.total
  return hr > lr
    ? { good: true, text: `60 分以上成了 ${pct(hr)}，60 分以下成了 ${pct(lr)}：分越高越容易成，推荐是靠谱的。` }
    : { good: false, text: `60 分以上只成了 ${pct(hr)}，不比 60 分以下（${pct(lr)}）高：去「匹配配置」调一下各项的权重，用试算对比一下再保存。` }
})

function pct(x: number) {
  return `${Math.round(x * 100)}%`
}
</script>

<template>
  <div class="calibration">
    <p v-if="total === 0" class="empty">
      还没有可以对比的记录。从这个版本起，每次发申请或邀请都会记下当时的匹配分，等处理完的多起来，这里就能看出推荐准不准。
    </p>
    <template v-else>
      <div v-for="r in rows" :key="r.from" class="row" :class="{ thin: !r.enough }">
        <span class="label num">{{ r.from }}–{{ r.to }} 分</span>
        <span class="bar-wrap">
          <span class="bar" :style="{ width: `${r.rate * 100}%` }" />
        </span>
        <span class="value">
          <template v-if="r.total">成了 <b class="num">{{ r.accepted }}/{{ r.total }}</b>（{{ pct(r.rate) }}）</template>
          <template v-else>没有</template>
          <small v-if="r.total && !r.enough">样本太少</small>
        </span>
      </div>
      <p v-if="verdict" class="verdict" :class="{ bad: !verdict.good }">{{ verdict.text }}</p>
      <p v-else class="hint">60 分以上和以下的记录都够 {{ MIN_SAMPLE }} 条以后，这里会给出结论。</p>
    </template>
    <p v-if="data.unscored" class="hint">另有 {{ data.unscored }} 条是开始记分之前处理的，不在图里。</p>
  </div>
</template>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 76px 1fr 170px;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
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
  background: var(--jd-olive);
}

.row.thin .bar {
  background: var(--jd-line-strong);
}

.value {
  font-size: 12px;
  color: var(--jd-ink-2);
}

.value small {
  margin-left: 4px;
  color: var(--jd-muted);
}

.verdict {
  margin: 12px 0 0;
  padding: 8px 12px;
  border-left: 2px solid var(--jd-olive);
  background: var(--jd-field);
  font-size: 12px;
  line-height: 1.6;
}

.verdict.bad {
  border-left-color: var(--jd-red);
  background: var(--jd-red-soft);
}

.hint,
.empty {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--jd-muted);
}

.empty {
  margin: 16px 0;
  text-align: center;
}

@media (max-width: 560px) {
  .row {
    grid-template-columns: 64px 1fr 120px;
  }
}
</style>

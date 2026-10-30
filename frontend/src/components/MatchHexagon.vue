<script setup lang="ts">
import { computed } from 'vue'
import type { MatchResult } from '@/types/api'

/**
 * 匹配六边形：匹配引擎的 5 个打分维度（目标与投入、能力与角色、协作风格、时间与节奏、责任与底线）加上投入保障（承诺系数 K）。
 * <p>
 * 没数据的维度画在 50 分（和引擎"数据越少越靠近 50"一致），用空心点、灰字标"没数据"，不画成 0 分；
 * 问卷没填全（完整度不到一半）的整个六边形画成虚线。最多叠两个：第一个实心，第二个虚线框，用来对比。
 */
const props = withDefaults(
  defineProps<{
    series: { name: string; match: MatchResult }[]
    /** 投入保障这一项说的是谁："TA" 或 "你" */
    commitmentOf?: string
  }>(),
  { commitmentOf: 'TA' },
)

const W = 340
const H = 250
const CX = W / 2
const CY = 122
const R = 82
const RINGS = [25, 50, 75, 100]
const COMMITMENT = '投入保障'

interface Axis {
  key: string
  name: string
}

/** 轴取第一个系列的维度顺序，最后一根是投入保障 */
const axes = computed<Axis[]>(() => {
  const dims = props.series[0]?.match.dimensions ?? []
  return [...dims.map((d) => ({ key: d.key, name: d.name })), { key: 'k', name: COMMITMENT }]
})

function angle(i: number): number {
  return -Math.PI / 2 + (2 * Math.PI * i) / axes.value.length
}

function point(i: number, value: number): [number, number] {
  const r = (R * Math.max(0, Math.min(100, value))) / 100
  return [CX + r * Math.cos(angle(i)), CY + r * Math.sin(angle(i))]
}

function ring(value: number): string {
  return axes.value.map((_, i) => point(i, value).join(',')).join(' ')
}

/** 一个系列在每根轴上的值；没数据的记为 null，画的时候放在 50 */
function values(m: MatchResult): (number | null)[] {
  return axes.value.map((a) => {
    if (a.key === 'k') return Math.round(m.k * 100)
    const score = m.dimensions.find((d) => d.key === a.key)?.score
    return score === null || score === undefined ? null : Math.round(score)
  })
}

const shapes = computed(() =>
  props.series.slice(0, 2).map((s, idx) => {
    const vals = values(s.match)
    const pts = vals.map((v, i) => point(i, v ?? 50))
    return {
      name: s.name,
      idx,
      vals,
      pts,
      polygon: pts.map((p) => p.join(',')).join(' '),
      sparse: s.match.completeness < 0.5,
    }
  }),
)

function labelPos(i: number): { x: number; y: number; anchor: 'start' | 'middle' | 'end' } {
  const a = angle(i)
  const x = CX + (R + 12) * Math.cos(a)
  const y = CY + (R + 12) * Math.sin(a)
  const c = Math.cos(a)
  const anchor = Math.abs(c) < 0.2 ? 'middle' : c > 0 ? 'start' : 'end'
  // 正上方的往上挪一点，正下方的往下挪一点，避免压住顶点
  const dy = Math.sin(a) < -0.9 ? -4 : Math.sin(a) > 0.9 ? 12 : 4
  return { x, y: y + dy, anchor }
}

/** 轴旁边的数字只写第一个系列的 */
function valueText(i: number): string {
  const v = shapes.value[0]?.vals[i]
  return v === null || v === undefined ? '没数据' : String(v)
}

function tip(name: string, axis: Axis, v: number | null): string {
  if (axis.key === 'k') {
    return `${name} · ${COMMITMENT} ${v}：${props.commitmentOf}的互评信用和备赛期间的考试、实习等算出来，乘在总分上`
  }
  return v === null ? `${name} · ${axis.name}：没数据（问卷里没答相关的题），按 50 分画` : `${name} · ${axis.name} ${v}`
}

const ariaLabel = computed(() =>
  shapes.value
    .map((s) => `${s.name}：` + axes.value.map((a, i) => `${a.name}${s.vals[i] === null ? '没数据' : s.vals[i]}`).join('，'))
    .join('；'),
)
</script>

<template>
  <figure class="hexagon">
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="`匹配六边形。${ariaLabel}`">
      <!-- 底网：25 / 50 / 75 / 100 四圈和六根轴 -->
      <polygon v-for="v in RINGS" :key="v" :points="ring(v)" class="grid" :class="{ mid: v === 50 }" />
      <line
        v-for="(a, i) in axes"
        :key="a.key"
        :x1="CX"
        :y1="CY"
        :x2="point(i, 100)[0]"
        :y2="point(i, 100)[1]"
        class="axis"
      />

      <g v-for="s in [...shapes].reverse()" :key="s.idx" :class="['shape', `s${s.idx}`, { sparse: s.sparse }]">
        <polygon :points="s.polygon" class="area" />
        <circle
          v-for="(p, i) in s.pts"
          :key="i"
          :cx="p[0]"
          :cy="p[1]"
          :r="s.idx === 0 ? 3.5 : 3"
          class="dot"
          :class="{ missing: s.vals[i] === null }"
        >
          <title>{{ tip(s.name, axes[i]!, s.vals[i] ?? null) }}</title>
        </circle>
      </g>

      <text
        v-for="(a, i) in axes"
        :key="'l' + a.key"
        :x="labelPos(i).x"
        :y="labelPos(i).y"
        :text-anchor="labelPos(i).anchor"
        class="label"
        :class="{ muted: shapes[0]?.vals[i] === null }"
      >
        {{ a.name }}<tspan class="value" dx="4">{{ valueText(i) }}</tspan>
      </text>
    </svg>

    <figcaption class="legend">
      <span v-for="s in shapes" :key="s.idx" class="legend-item" :class="`s${s.idx}`">
        <span class="swatch" aria-hidden="true" />{{ s.name }}
        <b class="num">{{ Math.round(series[s.idx]!.match.score) }}</b> 分
        <span v-if="s.sparse" class="sparse-note">（问卷没填全，仅供参考）</span>
      </span>
    </figcaption>
  </figure>
</template>

<style scoped>
.hexagon {
  margin: 0;
}

svg {
  display: block;
  width: 100%;
  max-width: 380px;
  margin: 0 auto;
  overflow: visible;
}

.grid {
  fill: none;
  stroke: var(--jd-line-lighter);
  stroke-width: 1;
}

.grid.mid {
  stroke: var(--jd-line);
  stroke-dasharray: 2 3;
}

.axis {
  stroke: var(--jd-line-lighter);
  stroke-width: 1;
}

.s0 .area {
  fill: var(--jd-accent);
  fill-opacity: 0.18;
  stroke: var(--jd-accent);
  stroke-width: 2;
}

.s1 .area {
  fill: none;
  stroke: var(--jd-ink);
  stroke-width: 1.5;
  stroke-dasharray: 5 3;
}

.sparse.s0 .area {
  stroke-dasharray: 4 3;
  fill-opacity: 0.08;
}

.dot {
  stroke-width: 1.5;
}

.s0 .dot {
  fill: var(--jd-accent);
  stroke: var(--jd-accent);
}

.s1 .dot {
  fill: var(--jd-ink);
  stroke: var(--jd-ink);
}

.dot.missing {
  fill: var(--jd-panel);
}

.label {
  font-size: 11px;
  fill: var(--jd-ink-2);
}

.label .value {
  font-weight: 700;
  fill: var(--jd-ink);
}

.label.muted,
.label.muted .value {
  fill: var(--jd-placeholder);
  font-weight: 400;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px 14px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--jd-ink-2);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.swatch {
  display: inline-block;
  width: 14px;
  height: 8px;
}

.s0 .swatch {
  background: var(--jd-accent);
  opacity: 0.6;
}

.s1 .swatch {
  border: 1.5px dashed var(--jd-ink);
}

.sparse-note {
  color: var(--jd-muted);
}
</style>

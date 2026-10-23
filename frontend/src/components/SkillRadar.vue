<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { LegendComponent, RadarComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([RadarChart, RadarComponent, LegendComponent, TooltipComponent, CanvasRenderer])

/** 一条要画的线：队伍要什么、我会什么，叠在一张图上比 */
export interface RadarSeries {
  name: string
  values: number[]
  color?: string
  /** 虚线：用来画"要求"，一眼和自己的能力区分开 */
  dashed?: boolean
}

/**
 * 六维能力雷达图。个人档案页、个人资料页、队伍招募页共用这一个组件：
 * 图上的分数都是后端算好的 0–100，组件只管画，不改规则。
 */
const props = defineProps<{
  axes: { label: string; score: number; basis?: string }[]
  series: RadarSeries[]
  height?: number
  /** 卡片里的小图：字号小、不要图例、不要多余的留白 */
  compact?: boolean
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

const height = computed(() => props.height ?? (props.compact ? 120 : 250))

const option = computed(() => ({
  animationDuration: 300,
  tooltip: {
    trigger: 'item',
    confine: true,
    formatter: (params: unknown) => {
      const p = params as { name?: string; value?: number[] }
      const values = p.value ?? []
      const rows = props.axes.map((a, i) => {
        const guessed = a.basis === 'INFERRED' || a.basis === 'BASE'
        return `${a.label}：<b>${values[i] ?? 0}</b>${guessed ? '（推断）' : ''}`
      })
      return `<b>${p.name ?? ''}</b><br/>${rows.join('<br/>')}`
    },
  },
  legend: {
    show: props.series.length > 1,
    bottom: 0,
    itemWidth: 10,
    itemHeight: 8,
    textStyle: { fontSize: 12, color: '#77756d' },
  },
  radar: {
    indicator: props.axes.map((a) => ({ name: a.label, max: 100 })),
    center: ['50%', props.series.length > 1 ? '46%' : '50%'],
    radius: props.compact ? '64%' : '64%',
    splitNumber: 4,
    axisName: { color: '#77756d', fontSize: props.compact ? 10 : 12 },
    axisLine: { lineStyle: { color: '#e5e1d8' } },
    splitLine: { lineStyle: { color: '#e5e1d8' } },
    splitArea: { areaStyle: { color: ['rgba(248,246,241,0.45)', 'rgba(255,255,255,0)'] } },
  },
  series: [
    {
      type: 'radar' as const,
      symbolSize: props.compact ? 2 : 3,
      data: props.series.map((s) => {
        const color = s.color ?? '#121310'
        return {
          name: s.name,
          value: s.values,
          lineStyle: { color, width: 1.5, type: s.dashed ? 'dashed' : 'solid' },
          itemStyle: { color },
          areaStyle: { color, opacity: props.series.length > 1 ? 0.08 : 0.14 },
        }
      }),
    },
  ],
}))

function render() {
  if (!chart) return
  chart.setOption(option.value, { notMerge: true })
  chart.resize()
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})

watch(option, render)
watch(height, () => requestAnimationFrame(() => chart?.resize()))
</script>

<template>
  <div
    ref="el"
    class="radar"
    :style="{ height: `${height}px` }"
    role="img"
    :aria-label="`能力雷达图：${axes.map((a) => `${a.label} ${a.score} 分`).join('，')}`"
  />
</template>

<style scoped>
.radar {
  width: 100%;
  min-width: 0;
}
</style>

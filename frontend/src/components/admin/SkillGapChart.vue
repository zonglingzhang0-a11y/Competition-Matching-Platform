<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { SkillGap } from '@/types/api'

echarts.use([BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

/**
 * 技能供需缺口图：横向条形图，每种技能两根条——
 * "队伍还需要"一根，"在找队的人"一根（按熟练度堆叠）。缺得最多的排在最上面。
 */
const props = defineProps<{ items: SkillGap[] }>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

/** 每种技能占 34px，少的时候也留够图例和坐标轴的位置 */
const height = computed(() => Math.max(220, props.items.length * 34 + 70))

function render() {
  if (!chart) return
  // 横向条形图的 y 轴从下往上画，倒过来让缺口最大的在最上面
  const rows = [...props.items].reverse()
  const levelSeries = ['入门', '熟练', '精通'].map((name, i) => ({
    name: `会的人·${name}`,
    type: 'bar' as const,
    stack: 'supply',
    barMaxWidth: 12,
    data: rows.map((r) => r.supplyByLevel[i]),
    // 会的人按熟练度由浅到深：暖灰、灰褐、近黑（竞搭配色）
    itemStyle: { color: ['#c8c3b8', '#77756d', '#121310'][i] },
  }))
  chart.setOption(
    {
      grid: { left: 8, right: 24, top: 36, bottom: 8, containLabel: true },
      legend: { top: 0, itemWidth: 12, itemHeight: 10 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: unknown) => {
          const list = params as { dataIndex: number }[]
          const row = rows[list[0]?.dataIndex ?? 0]
          if (!row) return ''
          const [l1, l2, l3] = row.supplyByLevel
          const verdict =
            row.gap > 0 ? `<b style="color:#b0442d">缺 ${row.gap} 人</b>` : row.gap < 0 ? `人手富余 ${-row.gap}` : '刚好够'
          return (
            `<b>${row.name}</b>（${row.category}）<br/>` +
            `队伍还需要：${row.demand} 人<br/>` +
            `在找队的人里会的：${row.supply} 人（入门 ${l1} / 熟练 ${l2} / 精通 ${l3}）<br/>${verdict}`
          )
        },
      },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: { type: 'category', data: rows.map((r) => r.name), axisTick: { show: false } },
      series: [
        {
          name: '队伍还需要',
          type: 'bar',
          barMaxWidth: 12,
          data: rows.map((r) => r.demand),
          // 缺口用砖红，和截止、警示同一个颜色
          itemStyle: { color: '#b0442d' },
        },
        ...levelSeries,
      ],
    },
    { notMerge: true },
  )
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

watch(() => props.items, render)
watch(height, () => requestAnimationFrame(() => chart?.resize()))
</script>

<template>
  <div ref="el" class="chart" :style="{ height: `${height}px` }" role="img" aria-label="技能供需缺口图" />
</template>

<style scoped>
.chart {
  width: 100%;
}
</style>

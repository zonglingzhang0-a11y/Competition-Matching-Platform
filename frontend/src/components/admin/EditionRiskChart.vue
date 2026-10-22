<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { ScatterChart } from 'echarts/charts'
import { GridComponent, MarkAreaComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EditionStat } from '@/types/api'
import { daysLeft, fmtDateTime } from '@/utils/time'

echarts.use([ScatterChart, GridComponent, MarkAreaComponent, TooltipComponent, CanvasRenderer])

/**
 * 各届组队风险图：横轴离报名截止还有几天，纵轴关注了这一届、还没进队的人数，气泡大小是招募中的队伍还差几个人。
 * 左上角（快截止了还有很多人没队伍）最该管：一周内截止、还有人没组队的气泡标成砖红。点气泡把下面的图切到这一届。
 * 只画报名还没截止、填了截止时间的届次。
 */
const props = defineProps<{ items: EditionStat[]; selected?: number }>()
const emit = defineEmits<{ pick: [editionId: number] }>()

/** 一周内截止算"快截止了" */
const SOON = 7

const rows = computed(() =>
  props.items
    .filter((e) => e.phase !== 'CLOSED' && e.registrationEnd)
    .map((e) => ({ ...e, days: Math.max(0, daysLeft(e.registrationEnd!)) })),
)

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

function render() {
  if (!chart) return
  const list = rows.value
  const maxDays = Math.max(SOON * 2, ...list.map((r) => r.days)) + 3
  chart.setOption(
    {
      grid: { left: 8, right: 24, top: 24, bottom: 28, containLabel: true },
      tooltip: {
        trigger: 'item',
        formatter: (p: unknown) => {
          const r = list[(p as { dataIndex: number }).dataIndex]
          if (!r) return ''
          return (
            `<b>${r.competitionName}</b> · ${r.editionTitle}<br/>` +
            `报名截止：${fmtDateTime(r.registrationEnd)}（还有 ${r.days} 天）<br/>` +
            `关注了、还没进队：<b>${r.seekers}</b> 人<br/>` +
            `招募中的队伍：${r.recruitingTeams} 支，还差 ${r.openSlots} 人<br/>` +
            `已经组好的队伍：${r.formedTeams} 支`
          )
        },
      },
      xAxis: {
        type: 'value',
        name: '离报名截止（天）',
        nameLocation: 'middle',
        nameGap: 24,
        min: 0,
        max: maxDays,
        minInterval: 1,
        splitLine: { lineStyle: { color: '#e0dbd1' } },
      },
      yAxis: {
        type: 'value',
        name: '还没组队的人',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#e0dbd1' } },
      },
      series: [
        {
          type: 'scatter',
          data: list.map((r) => ({
            value: [r.days, r.seekers],
            symbolSize: 12 + 7 * Math.sqrt(r.openSlots),
            itemStyle: {
              // 一周内截止、还有人没队伍：砖红；其余金棕。选中的那一届描黑边
              color: r.days <= SOON && r.seekers > 0 ? '#b0442d' : '#a67d48',
              opacity: 0.75,
              borderColor: r.editionId === props.selected ? '#121310' : 'transparent',
              borderWidth: 2,
            },
            label: {
              show: true,
              position: 'right',
              formatter: r.competitionName.replace(/^(全国|中国)?大学生/, '').slice(0, 8),
              color: '#3b3b35',
              fontSize: 11,
            },
          })),
          cursor: 'pointer',
          markArea: {
            silent: true,
            itemStyle: { color: 'rgba(176, 68, 45, 0.07)' },
            label: { position: 'insideTopLeft', color: '#b0442d', fontSize: 11 },
            data: [[{ name: `一周内截止`, xAxis: 0 }, { xAxis: SOON }]],
          },
        },
      ],
    },
    { notMerge: true },
  )
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  chart.on('click', (p) => {
    const r = rows.value[p.dataIndex]
    if (r) emit('pick', r.editionId)
  })
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})

watch(() => [props.items, props.selected], render)
</script>

<template>
  <div>
    <p v-if="rows.length === 0" class="empty">现在没有报名中、填了截止时间的届次。</p>
    <div v-show="rows.length > 0" ref="el" class="chart" role="img" aria-label="各届组队风险图：离报名截止的天数和还没组队的人数" />
  </div>
</template>

<style scoped>
.chart {
  width: 100%;
  height: 300px;
}

.empty {
  margin: 24px 0;
  font-size: 13px;
  color: var(--jd-muted);
  text-align: center;
}
</style>

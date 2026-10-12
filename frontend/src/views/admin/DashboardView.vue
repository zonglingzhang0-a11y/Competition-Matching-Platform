<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dashboardEditions, dashboardOverview, skillGap } from '@/api/admin'
import SkillGapChart from '@/components/admin/SkillGapChart.vue'
import type { EditionPhase, EditionStat, Overview, SkillGap } from '@/types/api'
import { PHASE } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

const overview = ref<Overview | null>(null)
const editions = ref<EditionStat[]>([])
const gaps = ref<SkillGap[]>([])
/** 不选就是所有报名中的届次 */
const editionId = ref<number | undefined>()
const loading = ref(false)
const gapLoading = ref(false)

const cards = computed(() => {
  const o = overview.value
  if (!o) return []
  const accepted = o.applications.ACCEPTED ?? 0
  const decided = accepted + (o.applications.REJECTED ?? 0)
  return [
    { label: '账号', value: o.users, sub: `已认证 ${o.verifiedUsers} · 近 7 天新增 ${o.newUsers7d}` },
    { label: '待审核认证', value: o.pendingVerify, sub: '学校还没收录的教育邮箱', link: '/admin/verifications', warn: o.pendingVerify > 0 },
    { label: '填过问卷', value: o.surveyDone, sub: o.users ? `占全部账号 ${Math.round((o.surveyDone / o.users) * 100)}%` : '' },
    { label: '招募中的队伍', value: o.teams.RECRUITING ?? 0, sub: `已满员 ${o.teams.FULL ?? 0} · 停止招募 ${o.teams.CLOSED ?? 0}` },
    { label: '还差的人数', value: o.openSlots, sub: '招募中队伍没招满的名额' },
    {
      label: '待处理的申请和邀请',
      value: o.applications.PENDING ?? 0,
      sub: `已促成入队 ${accepted} 次` + (decided ? ` · 通过率 ${Math.round((accepted / decided) * 100)}%` : ''),
    },
    { label: '比赛', value: o.competitions, sub: `报名中的届次 ${o.openEditions}` },
  ]
})

/** 下拉框列出所有已发布的届次 */
const editionOptions = computed(() =>
  editions.value.map((e) => ({ value: e.editionId, label: `${e.competitionName} · ${e.editionTitle}` })),
)

const shortage = computed(() => gaps.value.filter((g) => g.gap > 0).slice(0, 3))

async function load() {
  loading.value = true
  try {
    const [o, e] = await Promise.all([dashboardOverview(), dashboardEditions()])
    overview.value = o
    editions.value = e
  } catch (err) {
    showError(err)
  } finally {
    loading.value = false
  }
  loadGap()
}

async function loadGap() {
  gapLoading.value = true
  try {
    gaps.value = await skillGap(editionId.value)
  } catch (err) {
    showError(err)
  } finally {
    gapLoading.value = false
  }
}

function pickEdition(row: EditionStat) {
  editionId.value = row.editionId
}

watch(editionId, loadGap)
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="cards" v-loading="loading">
      <component
        :is="card.link ? 'router-link' : 'div'"
        v-for="card in cards"
        :key="card.label"
        :to="card.link"
        class="card"
        :class="{ warn: card.warn, link: !!card.link }"
      >
        <div class="label">{{ card.label }}</div>
        <div class="value">{{ card.value }}</div>
        <div class="sub">{{ card.sub }}</div>
      </component>
    </div>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="header">
          <span>技能供需缺口</span>
          <el-select v-model="editionId" clearable placeholder="所有报名中的届次" class="edition-select">
            <el-option v-for="o in editionOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>
      </template>
      <p class="hint">
        橙色是队伍招募需求里还缺的人数，蓝色是还在找队的人里会这项技能的人数（关注了这届、已认证、还没进队）。
        <template v-if="!editionId">跨届汇总时，一个人同时在两届找队会算两次。</template>
      </p>
      <div v-loading="gapLoading">
        <el-empty v-if="!gapLoading && gaps.length === 0" description="还没有招募需求，也没有在找队的人" />
        <template v-else>
          <el-alert
            v-if="shortage.length"
            type="warning"
            :closable="false"
            show-icon
            class="shortage"
            :title="'最缺的技能：' + shortage.map((g) => `${g.name}（差 ${g.gap} 人）`).join('、')"
          />
          <SkillGapChart :items="gaps" />
        </template>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>各届组队情况</template>
      <el-table :data="editions" empty-text="还没有发布的届次" class="clickable-rows" @row-click="pickEdition">
        <el-table-column label="比赛" min-width="200">
          <template #default="{ row }">
            <div>{{ row.competitionName }}</div>
            <div class="muted">{{ row.editionTitle }}</div>
          </template>
        </el-table-column>
        <el-table-column label="报名" width="150">
          <template #default="{ row }">
            <el-tag :type="PHASE[row.phase as EditionPhase].type" size="small">
              {{ PHASE[row.phase as EditionPhase].label }}
            </el-tag>
            <div v-if="row.registrationEnd" class="muted">截止 {{ fmtDateTime(row.registrationEnd) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="recruitingTeams" label="招募中" width="80" align="right" />
        <el-table-column prop="formedTeams" label="已成队" width="80" align="right" />
        <el-table-column prop="openSlots" label="还差人数" width="90" align="right" />
        <el-table-column prop="seekers" label="在找队" width="80" align="right" />
        <el-table-column prop="followers" label="关注" width="70" align="right" />
      </el-table>
      <p class="hint table-hint">点一行，上面的缺口图就只看这一届。</p>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  min-height: 90px;
}

.card {
  display: block;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  color: inherit;
  text-decoration: none;
}

.card.link:hover {
  border-color: var(--el-color-primary-light-5);
}

.card.warn .value {
  color: var(--el-color-warning);
}

.label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.value {
  margin: 4px 0 2px;
  font-size: 26px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.block {
  margin-bottom: 16px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.edition-select {
  width: 300px;
  max-width: 100%;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.table-hint {
  margin: 8px 0 0;
}

.shortage {
  margin-bottom: 8px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.clickable-rows :deep(.el-table__row) {
  cursor: pointer;
}
</style>

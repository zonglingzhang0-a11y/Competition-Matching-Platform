<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { myCredit, myReports, myRounds } from '@/api/review'
import type { CreditView, MyReport, RoundSummary } from '@/types/api'
import { REPORT_REASON, REPORT_STATUS, creditType } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/** 互评与信用：我的信用分、我参与的互评、我提交的举报 */
type Tab = 'credit' | 'rounds' | 'reports'

const route = useRoute()
const router = useRouter()

const tab = ref<Tab>((route.query.tab as Tab) || 'credit')
const credit = ref<CreditView | null>(null)
const rounds = ref<RoundSummary[]>([])
const reports = ref<MyReport[]>([])
const loading = ref(false)

const FREQ = ['从未', '偶尔', '有时', '经常']

const todo = computed(() => rounds.value.filter((r) => r.status === 'OPEN' && !r.submitted).length)

/** 平均频率换成最接近的档位文字；有人提过但平均还不到"偶尔"的，写"很少" */
function freqText(v: number) {
  if (v > 0 && v < 0.5) return '很少'
  return FREQ[Math.min(3, Math.max(0, Math.round(v)))]
}

async function load() {
  loading.value = true
  try {
    ;[credit.value, rounds.value, reports.value] = await Promise.all([myCredit(), myRounds(), myReports()])
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function openRound(r: RoundSummary) {
  if (r.status === 'OPEN') router.push({ name: 'review-form', params: { id: r.id } })
  else router.push({ name: 'team', params: { id: r.teamId } })
}

watch(tab, (t) => router.replace({ query: { tab: t } }))
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader en="REVIEWS" zh="互评与信用" />
    <el-tabs v-model="tab">
      <el-tab-pane label="我的信用" name="credit" />
      <el-tab-pane :label="todo ? `互评（${todo} 个待填）` : '互评'" name="rounds" />
      <el-tab-pane label="我的举报" name="reports" />
    </el-tabs>

    <!-- 我的信用 -->
    <template v-if="tab === 'credit' && credit">
      <el-alert
        v-if="credit.suspendedUntil"
        type="error"
        :closable="false"
        show-icon
        class="block"
        :title="`你被暂停推荐到 ${fmtDateTime(credit.suspendedUntil, true)}`"
        description="这段时间里你不会出现在队长的推荐列表里，但仍然可以自己找队伍、申请入队。如有误会可以通过举报说明情况。"
      />
      <el-card shadow="never" class="block">
        <div class="score-row">
          <div class="score">
            <template v-if="credit.score !== null">
              <span class="number" :class="creditType(credit.score)">{{ credit.score.toFixed(1) }}</span>
              <span class="unit">/ 100</span>
            </template>
            <span v-else class="number new">新用户</span>
          </div>
          <div class="facts">
            <div>参加互评 <b>{{ credit.ratedRounds }}</b> 场，共 <b>{{ credit.raters }}</b> 位队友评价过你</div>
            <div>推荐时的信用系数 <b>× {{ credit.factor }}</b></div>
            <div class="muted">
              {{ credit.score === null ? '还没有互评记录，按满分算。' : '信用分越高，系数越接近 1；系数会乘到匹配分上。' }}
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>队友眼里的你</template>
        <template v-if="credit.itemsVisible && credit.items">
          <div v-for="(name, i) in credit.itemNames" :key="i" class="item">
            <span class="item-name">{{ name }}</span>
            <div class="bar" :aria-label="`平均 ${credit.items[i]}`">
              <div class="fill" :style="{ width: `${((credit.items[i] ?? 0) / 3) * 100}%` }" />
            </div>
            <span class="freq">{{ freqText(credit.items[i] ?? 0) }}</span>
            <span v-if="credit.selfItems" class="self">你自评：{{ freqText(credit.selfItems[i] ?? 0) }}</span>
          </div>
          <p class="muted note">条越短越好。自评只有你自己能看到，用来对照别人眼里的你。</p>
        </template>
        <el-empty v-else :image-size="60">
          <template #description>
            <p class="muted">
              再参加一场比赛的互评，或者累计 4 位队友评价过你之后，才能看到每一项的具体评价。<br />
              小队人少，太早显示容易猜出是谁打的分。
            </p>
          </template>
        </el-empty>
      </el-card>
    </template>

    <!-- 互评 -->
    <template v-if="tab === 'rounds'">
      <el-empty v-if="rounds.length === 0" description="还没有互评。队长在队伍页标记比赛结束后，就会开启互评。" />
      <div v-for="r in rounds" :key="r.id" class="round" @click="openRound(r)">
        <div class="round-main">
          <div class="round-title">{{ r.teamName }}</div>
          <div class="muted">{{ r.competitionName }} {{ r.editionTitle }}</div>
        </div>
        <div class="round-status">
          <template v-if="r.status === 'OPEN'">
            <el-tag v-if="!r.submitted" type="warning">待填写</el-tag>
            <el-tag v-else type="info">已交，等其他人</el-tag>
            <div class="muted">{{ r.raters }}/{{ r.participants }} 人已交 · {{ fmtDateTime(r.closesAt) }} 截止</div>
          </template>
          <template v-else>
            <el-tag type="success">已揭晓</el-tag>
            <div class="muted">{{ fmtDateTime(r.publishedAt) }}</div>
          </template>
        </div>
      </div>
    </template>

    <!-- 我的举报 -->
    <template v-if="tab === 'reports'">
      <el-empty v-if="reports.length === 0" description="你还没有举报过。在队伍页可以举报队伍或队员。" />
      <el-card v-for="r in reports" :key="r.id" shadow="never" class="block">
        <div class="report-head">
          <span>
            {{ r.targetType === 'TEAM' ? '队伍' : '用户' }}「{{ r.targetName }}」 · {{ REPORT_REASON[r.reason] }}
          </span>
          <el-tag :type="REPORT_STATUS[r.status].type" size="small">{{ REPORT_STATUS[r.status].label }}</el-tag>
        </div>
        <p v-if="r.detail" class="detail">{{ r.detail }}</p>
        <p v-if="r.resultNote" class="result">管理员：{{ r.resultNote }}</p>
        <div class="muted">
          {{ fmtDateTime(r.createdAt) }} 提交<template v-if="r.handledAt"> · {{ fmtDateTime(r.handledAt) }} 处理</template>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
  margin: 0 auto;
  min-height: 200px;
}

.block {
  border-radius: 2px;
  margin-bottom: 16px;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 32px;
  flex-wrap: wrap;
}

.number {
  font-size: 40px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.number.success {
  color: var(--el-color-success);
}

.number.warning {
  color: var(--el-color-warning);
}

.number.danger {
  color: var(--el-color-danger);
}

.number.new {
  font-size: 28px;
  color: var(--el-text-color-regular);
}

.unit {
  margin-left: 4px;
  color: var(--el-text-color-secondary);
}

.facts {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.item {
  display: grid;
  grid-template-columns: 200px 1fr 40px 110px;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  font-size: 14px;
}

.bar {
  height: 8px;
  border-radius: 2px;
  background: var(--el-fill-color);
  overflow: hidden;
}

.fill {
  height: 100%;
  background: var(--el-color-warning);
}

.freq {
  font-size: 13px;
}

.self {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.note {
  margin: 8px 0 0;
}

.round {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 2px;
  background: var(--el-bg-color);
  cursor: pointer;
}

.round:hover {
  border-color: var(--el-color-primary-light-5);
}

.round-title {
  font-weight: 500;
}

.round-status {
  text-align: right;
}

.report-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.detail,
.result {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

.result {
  color: var(--el-color-primary);
}

@media (max-width: 640px) {
  .item {
    grid-template-columns: 1fr 40px;
  }

  .bar,
  .self {
    grid-column: 1 / -1;
  }
}
</style>

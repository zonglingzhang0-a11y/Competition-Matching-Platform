<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { followEdition, listCompetitions, unfollowEdition } from '@/api/competition'
import type { CompetitionQuery, CompetitionSummary, EditionBrief } from '@/types/api'
import { CATEGORIES, LEVEL_LABEL, PHASE } from '@/utils/labels'
import { daysLeft, fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

const router = useRouter()
const route = useRoute()

const query = reactive<CompetitionQuery>({
  // 顶栏的搜索框跳过来时带着 ?keyword=
  keyword: (route.query.keyword as string) || '',
  category: undefined,
  level: undefined,
  openOnly: false,
  followedOnly: false,
  page: 1,
  size: 10,
})
const rows = ref<CompetitionSummary[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listCompetitions({ ...query, keyword: query.keyword?.trim() || undefined })
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

/** 改筛选条件时回到第一页 */
function search() {
  query.page = 1
  load()
}

function deadlineText(e: EditionBrief): string {
  if (!e.registrationEnd) return '报名截止时间待定'
  if (e.phase === 'OPEN' || e.phase === 'CLOSING_SOON') {
    const left = daysLeft(e.registrationEnd)
    return `报名截止 ${fmtDateTime(e.registrationEnd)}（${left <= 1 ? '今明两天' : `还剩 ${left} 天`}）`
  }
  return `报名截止 ${fmtDateTime(e.registrationEnd)}`
}

async function toggleFollow(e: EditionBrief) {
  try {
    if (e.followed) await unfollowEdition(e.id)
    else await followEdition(e.id)
    e.followed = !e.followed
    if (query.followedOnly && !e.followed) load()
  } catch (err) {
    showError(err)
  }
}

onMounted(load)
watch(
  () => route.query.keyword,
  (kw) => {
    query.keyword = (kw as string) || ''
    query.page = 1
    load()
  },
)
</script>

<template>
  <div class="page">
    <PageHeader en="COMPETITIONS" zh="比赛库" />
    <div class="filters">
      <el-input
        v-model="query.keyword"
        placeholder="搜比赛名称或简称"
        :prefix-icon="Search"
        clearable
        class="search"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.category" placeholder="学科类别" clearable class="select" @change="search">
        <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="query.level" placeholder="比赛级别" clearable class="select" @change="search">
        <el-option v-for="(label, value) in LEVEL_LABEL" :key="value" :label="label" :value="value" />
      </el-select>
      <el-checkbox v-model="query.openOnly" @change="search">只看还能报名的</el-checkbox>
      <el-checkbox v-model="query.followedOnly" @change="search">只看我关注的</el-checkbox>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && rows.length === 0" description="没有符合条件的比赛" />
      <el-card
        v-for="c in rows"
        :key="c.id"
        shadow="hover"
        class="item"
        @click="router.push({ name: 'competition', params: { id: c.id } })"
      >
        <div class="item-head">
          <div class="name">
            {{ c.name }}
            <span v-if="c.shortName" class="short">（{{ c.shortName }}）</span>
          </div>
          <el-button
            v-if="c.currentEdition"
            :type="c.currentEdition.followed ? 'primary' : 'default'"
            :plain="c.currentEdition.followed"
            size="small"
            @click.stop="toggleFollow(c.currentEdition)"
          >
            {{ c.currentEdition.followed ? '已关注' : '关注' }}
          </el-button>
        </div>
        <div class="tags">
          <el-tag size="small" effect="plain">{{ LEVEL_LABEL[c.level] }}</el-tag>
          <el-tag size="small" effect="plain" type="info">{{ c.category }}</el-tag>
          <el-tag v-if="c.inCaheList" size="small" effect="plain" type="success">高教学会竞赛目录</el-tag>
          <el-tag v-if="c.recognition" size="small" type="warning" effect="plain" title="本校同学标注、多人确认的加分认定级别">
            本校 {{ c.recognition }}
          </el-tag>
        </div>
        <div v-if="c.currentEdition" class="edition">
          <el-tag size="small" :type="PHASE[c.currentEdition.phase].type">{{ PHASE[c.currentEdition.phase].label }}</el-tag>
          <span>{{ c.currentEdition.title }}</span>
          <span class="dot">·</span>
          <span>每队 {{ c.currentEdition.teamMin }}–{{ c.currentEdition.teamMax }} 人</span>
          <span class="dot">·</span>
          <span :class="{ urgent: c.currentEdition.phase === 'CLOSING_SOON' }">{{ deadlineText(c.currentEdition) }}</span>
        </div>
        <div v-else class="edition muted">还没有发布届次信息</div>
      </el-card>
    </div>

    <el-pagination
      v-if="total > query.size"
      v-model:current-page="query.page"
      :page-size="query.size"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.page {
  max-width: 880px;
  margin: 0 auto;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.search {
  width: 240px;
}

.select {
  width: 130px;
}

.list {
  min-height: 200px;
}

.item {
  border-radius: 2px;
  margin-bottom: 12px;
  cursor: pointer;
}

.item-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.name {
  font-size: 16px;
  font-weight: 500;
}

.short {
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.tags {
  display: flex;
  gap: 6px;
  margin: 8px 0;
}

.edition {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.dot {
  color: var(--el-text-color-placeholder);
}

.urgent {
  color: var(--el-color-warning);
}

.muted {
  color: var(--el-text-color-secondary);
}

.pager {
  justify-content: center;
  margin-top: 8px;
}
</style>

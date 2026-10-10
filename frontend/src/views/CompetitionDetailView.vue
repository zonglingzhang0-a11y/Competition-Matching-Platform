<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Link } from '@element-plus/icons-vue'
import { followEdition, getCompetition, unfollowEdition } from '@/api/competition'
import type { CompetitionDetail, CompetitionNode, EditionDetail } from '@/types/api'
import { LEVEL_LABEL, NODE_TYPE_LABEL, PHASE } from '@/utils/labels'
import { fmtDateTime, isFuzzy, isPast, nodeTimeText } from '@/utils/time'
import { showError } from '@/utils/feedback'

const route = useRoute()
const router = useRouter()

const detail = ref<CompetitionDetail | null>(null)
const loading = ref(true)
const activeEdition = ref<number>()

async function load() {
  loading.value = true
  try {
    detail.value = await getCompetition(Number(route.params.id))
    activeEdition.value = detail.value.editions[0]?.id
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function toggleFollow(e: EditionDetail) {
  try {
    if (e.followed) {
      await unfollowEdition(e.id)
      e.followers -= 1
    } else {
      await followEdition(e.id)
      e.followers += 1
    }
    e.followed = !e.followed
  } catch (err) {
    showError(err)
  }
}

function rules(e: EditionDetail): string[] {
  const list = [`每队 ${e.teamMin === e.teamMax ? e.teamMax : `${e.teamMin}–${e.teamMax}`} 人`]
  list.push(e.sameSchoolRequired ? '限同校组队' : '可以跨校组队')
  if (e.advisorRequired) list.push('需要指导老师')
  if (e.englishRequired) list.push('英文赛题')
  return list
}

/** 时间线圆点：已过去的灰色，比赛期间的标重点色 */
function nodeColor(n: CompetitionNode): string | undefined {
  if (isPast(n)) return 'var(--el-text-color-placeholder)'
  return n.busy ? 'var(--el-color-primary)' : undefined
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <el-button text :icon="ArrowLeft" class="back" @click="router.push({ name: 'competitions' })">比赛库</el-button>

    <template v-if="detail">
      <el-card shadow="never" class="block">
        <h1 class="title">
          {{ detail.name }}
          <span v-if="detail.shortName" class="short">（{{ detail.shortName }}）</span>
        </h1>
        <div class="tags">
          <el-tag size="small" effect="plain">{{ LEVEL_LABEL[detail.level] }}</el-tag>
          <el-tag size="small" effect="plain" type="info">{{ detail.category }}</el-tag>
          <el-tag v-if="detail.inCaheList" size="small" effect="plain" type="success">高教学会竞赛目录</el-tag>
        </div>
        <el-descriptions :column="1" class="meta">
          <el-descriptions-item v-if="detail.organizer" label="主办单位">{{ detail.organizer }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.roles.length" label="常见分工">{{ detail.roles.join(' / ') }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.officialUrl" label="官网">
            <el-link :href="detail.officialUrl" target="_blank" :icon="Link">{{ detail.officialUrl }}</el-link>
          </el-descriptions-item>
        </el-descriptions>
        <p v-if="detail.description" class="desc">{{ detail.description }}</p>
      </el-card>

      <el-empty v-if="detail.editions.length === 0" description="这个比赛还没有发布届次信息" />

      <el-card v-else shadow="never" class="block">
        <el-tabs v-model="activeEdition">
          <el-tab-pane v-for="e in detail.editions" :key="e.id" :label="e.title" :name="e.id">
            <div class="edition-head">
              <div>
                <el-tag :type="PHASE[e.phase].type">{{ PHASE[e.phase].label }}</el-tag>
                <span v-if="e.registrationEnd" class="deadline">
                  报名
                  <template v-if="e.registrationStart">{{ fmtDateTime(e.registrationStart) }} 至 </template>
                  {{ fmtDateTime(e.registrationEnd) }}
                </span>
              </div>
              <el-button :type="e.followed ? 'primary' : 'default'" :plain="e.followed" @click="toggleFollow(e)">
                {{ e.followed ? '已关注' : '关注这一届' }}
                <span class="followers">{{ e.followers }}</span>
              </el-button>
            </div>
            <div class="rules">
              <el-tag v-for="r in rules(e)" :key="r" type="info" effect="plain">{{ r }}</el-tag>
            </div>
            <p class="hint">关注后，这一届的时间节点会出现在你的日历里，报名截止前也会提醒你。</p>

            <h3 class="sub">时间节点</h3>
            <el-empty v-if="e.nodes.length === 0" :image-size="60" description="官方还没公布时间安排" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="n in e.nodes"
                :key="n.id"
                :timestamp="nodeTimeText(n)"
                :color="nodeColor(n)"
                :hollow="isFuzzy(n)"
                placement="top"
              >
                <span :class="{ past: isPast(n) }">{{ n.title }}</span>
                <el-tag size="small" type="info" effect="plain" class="node-tag">{{ NODE_TYPE_LABEL[n.nodeType] }}</el-tag>
                <el-tag v-if="n.busy" size="small" effect="plain" class="node-tag">比赛期间，参与撞期检测</el-tag>
                <el-tag v-if="isFuzzy(n)" size="small" type="warning" effect="plain" class="node-tag">时间未定</el-tag>
                <div v-if="n.note" class="note">{{ n.note }}</div>
              </el-timeline-item>
            </el-timeline>

            <el-link v-if="e.noticeUrl" :href="e.noticeUrl" target="_blank" :icon="Link">查看官方通知</el-link>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 880px;
  margin: 0 auto;
  min-height: 200px;
}

.back {
  margin-bottom: 8px;
}

.block {
  border-radius: 12px;
  margin-bottom: 16px;
}

.title {
  font-size: 22px;
  font-weight: 500;
  margin: 0;
}

.short {
  color: var(--el-text-color-secondary);
  font-weight: 400;
  font-size: 18px;
}

.tags {
  display: flex;
  gap: 6px;
  margin: 10px 0;
}

.meta {
  margin-top: 8px;
}

.desc {
  margin: 8px 0 0;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}

.edition-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.deadline {
  margin-left: 8px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.followers {
  margin-left: 6px;
  font-size: 12px;
  opacity: 0.75;
}

.rules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 10px 0 0;
}

.sub {
  font-size: 15px;
  font-weight: 500;
  margin: 20px 0 12px;
}

.node-tag {
  margin-left: 6px;
}

.past {
  color: var(--el-text-color-secondary);
}

.note {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

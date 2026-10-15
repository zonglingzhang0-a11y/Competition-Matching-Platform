<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import MatchSummary from '@/components/MatchSummary.vue'
import { recommendedTeams } from '@/api/survey'
import type { EditionDetail, TeamRecommendations } from '@/types/api'
import { TEAM_STATUS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

const props = defineProps<{ edition: EditionDetail }>()

const route = useRoute()
const data = ref<TeamRecommendations | null>(null)
const loading = ref(true)
const expanded = ref(false)

const visible = computed(() => (expanded.value ? data.value?.items : data.value?.items.slice(0, 3)) ?? [])
const surveyMissing = computed(() => data.value && (!data.value.generalSurveyDone || !data.value.editionSurveyDone))

async function load() {
  loading.value = true
  try {
    data.value = await recommendedTeams(props.edition.id)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-if="edition.phase !== 'CLOSED'" v-loading="loading" class="wrap">
    <h3 class="title">为你推荐</h3>

    <template v-if="data">
      <el-alert v-if="data.currentTeamId" type="success" :closable="false" show-icon>
        <template #title>
          你已经在这一届的队伍里了，
          <router-link :to="{ name: 'team', params: { id: data.currentTeamId } }">去看看你的队伍</router-link>
        </template>
      </el-alert>

      <template v-else>
        <el-alert v-if="data.note" type="warning" :closable="false" show-icon :title="data.note" class="gap" />

        <el-alert v-if="surveyMissing" type="info" :closable="false" class="gap">
          <template #title>填一下问卷，推荐会准很多</template>
          <div class="survey-links">
            <router-link v-if="!data.generalSurveyDone" :to="{ name: 'survey', query: { redirect: route.fullPath } }">
              组队问卷（快速版 1 分钟）
            </router-link>
            <router-link
              v-if="!data.editionSurveyDone"
              :to="{ name: 'survey-edition', params: { id: edition.id }, query: { redirect: route.fullPath } }"
            >
              这场比赛的参赛意向
            </router-link>
          </div>
        </el-alert>

        <el-empty v-if="data.items.length === 0 && !data.note" :image-size="60" description="这一届暂时没有你能加入的队伍" />

        <div v-for="r in visible" :key="r.team.id" class="item">
          <div class="item-head">
            <router-link :to="{ name: 'team', params: { id: r.team.id } }" class="name">{{ r.team.name }}</router-link>
            <el-tag size="small" :type="TEAM_STATUS[r.team.status].type">{{ TEAM_STATUS[r.team.status].label }}</el-tag>
            <span class="meta">
              {{ r.team.memberCount }}/{{ r.team.maxMembers }} 人 · 队长 {{ r.team.captain.nickname }}
              <template v-if="r.team.openNeeds.length"> · 在招 {{ r.team.openNeeds.map((n) => n.roleName).join('、') }}</template>
            </span>
          </div>
          <MatchSummary :match="r.match" compact />
        </div>
        <el-button v-if="data.items.length > 3" text @click="expanded = !expanded">
          {{ expanded ? '收起' : `看全部 ${data.items.length} 支` }}
        </el-button>
      </template>
    </template>
  </div>
</template>

<style scoped>
.wrap {
  margin-top: 24px;
  min-height: 60px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 12px;
}

.gap {
  margin-bottom: 12px;
}

.survey-links {
  display: flex;
  gap: 16px;
  margin-top: 4px;
}

.item {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 2px;
  margin-bottom: 10px;
}

.item-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.name {
  font-size: 15px;
  font-weight: 500;
}

.meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import MatchSummary from '@/components/MatchSummary.vue'
import { homeRecommendations, surveyProgress } from '@/api/survey'
import { useUserStore } from '@/stores/user'
import type { HomeRecommendation } from '@/types/api'
import { showError } from '@/utils/feedback'

const user = useUserStore()

const recs = ref<HomeRecommendation[]>([])
const generalDone = ref(true)
const generalQuick = ref(false)
const loading = ref(true)

onMounted(async () => {
  try {
    const [r, p] = await Promise.all([homeRecommendations(), surveyProgress()])
    recs.value = r
    generalDone.value = p.generalDone
    generalQuick.value = p.generalQuick
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="home">
    <h1 class="greeting">你好，{{ user.me?.nickname }}</h1>

    <el-alert
      v-if="user.me?.verifyStatus === 'NONE'"
      type="warning"
      :closable="false"
      show-icon
      title="完成学校认证后，就能建队和申请入队"
      class="notice"
    >
      <router-link to="/verify">去认证</router-link>
    </el-alert>

    <el-alert v-if="!loading && !generalDone" type="info" :closable="false" show-icon class="notice">
      <template #title>先花 1 分钟填一下组队问卷，推荐队友时才知道你们合不合得来</template>
      <router-link to="/survey">去填问卷</router-link>
    </el-alert>
    <el-alert v-else-if="!loading && generalQuick" type="info" :closable="false" class="notice">
      <template #title>你填的是快速版，补全完整版后推荐会更准</template>
      <router-link to="/survey">补全问卷</router-link>
    </el-alert>

    <section v-loading="loading" class="section">
      <h2 class="section-title">为你推荐</h2>
      <el-empty v-if="!loading && recs.length === 0" :image-size="60">
        <template #description>
          去<router-link to="/competitions">比赛库</router-link>关注感兴趣的比赛，这里会推荐适合你的队伍
        </template>
      </el-empty>
      <el-card v-for="r in recs" :key="r.edition.editionId" shadow="never" class="card">
        <template #header>
          <router-link :to="{ name: 'competition', params: { id: r.edition.competitionId } }" class="edition">
            {{ r.edition.competitionName }} · {{ r.edition.editionTitle }}
          </router-link>
        </template>
        <el-empty v-if="r.teams.length === 0" :image-size="40" description="这一届暂时没有你能加入的队伍，也可以自己建一支" />
        <div v-for="t in r.teams" :key="t.team.id" class="item">
          <router-link :to="{ name: 'team', params: { id: t.team.id } }" class="team-name">{{ t.team.name }}</router-link>
          <span class="meta">
            {{ t.team.memberCount }}/{{ t.team.maxMembers }} 人
            <template v-if="t.team.openNeeds.length"> · 在招 {{ t.team.openNeeds.map((n) => n.roleName).join('、') }}</template>
          </span>
          <MatchSummary :match="t.match" compact class="match" />
        </div>
      </el-card>
    </section>

    <div class="shortcuts">
      <router-link to="/competitions" class="shortcut">
        <el-card shadow="hover">
          <div class="shortcut-title">逛比赛库</div>
          <div class="shortcut-desc">看看哪些比赛正在报名，关注感兴趣的那一届</div>
        </el-card>
      </router-link>
      <router-link to="/profile" class="shortcut">
        <el-card shadow="hover">
          <div class="shortcut-title">完善档案</div>
          <div class="shortcut-desc">填好技能和参赛履历，推荐队友时会用到</div>
        </el-card>
      </router-link>
      <router-link to="/survey" class="shortcut">
        <el-card shadow="hover">
          <div class="shortcut-title">组队问卷</div>
          <div class="shortcut-desc">作息、协作习惯、底线，看你和队友合不合得来</div>
        </el-card>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.home {
  max-width: 760px;
  margin: 0 auto;
}

.greeting {
  font-size: 22px;
  font-weight: 500;
  margin: 0 0 16px;
}

.notice {
  margin-bottom: 16px;
}

.section {
  min-height: 80px;
  margin-bottom: 24px;
}

.section-title {
  font-size: 17px;
  font-weight: 500;
  margin: 0 0 12px;
}

.card {
  border-radius: 12px;
  margin-bottom: 12px;
}

.edition {
  font-weight: 500;
}

.item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.item:last-child {
  border-bottom: none;
}

.team-name {
  font-weight: 500;
  margin-right: 8px;
}

.meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.match {
  margin-top: 8px;
}

.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.shortcut {
  text-decoration: none;
}

.shortcut :deep(.el-card) {
  border-radius: 12px;
  height: 100%;
}

.shortcut-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.shortcut-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import TeamCard from '@/components/TeamCard.vue'
import { acceptApplication, myApplications, myTeams, rejectApplication, withdrawApplication } from '@/api/team'
import { myTasks, setTaskDone } from '@/api/calendar'
import type { ApplicationView, MyApplications, MyTask, TeamSummary } from '@/types/api'
import { APP_STATUS } from '@/utils/labels'
import { fmtDate, fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

const teams = ref<TeamSummary[]>([])
const apps = ref<MyApplications>({ sent: [], received: [] })
/** 分给我、还没做完的任务 */
const tasks = ref<MyTask[]>([])
const loading = ref(true)

const pendingInvites = computed(() => apps.value.received.filter((a) => a.status === 'PENDING'))
const otherInvites = computed(() => apps.value.received.filter((a) => a.status !== 'PENDING'))

async function load() {
  loading.value = true
  try {
    ;[teams.value, apps.value, tasks.value] = await Promise.all([myTeams(), myApplications(), myTasks()])
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function act(action: () => Promise<void>, success: string) {
  try {
    await action()
    ElMessage.success(success)
    await load()
  } catch (e) {
    showError(e)
  }
}

const teamLine = (a: ApplicationView) => `${a.team.competitionName} · ${a.team.editionTitle}`

/** 勾完成后从列表里拿掉 */
async function finish(t: MyTask) {
  try {
    await setTaskDone(t.id, true)
    tasks.value = tasks.value.filter((x) => x.id !== t.id)
    ElMessage.success(`「${t.title}」完成了`)
  } catch (e) {
    showError(e)
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader en="MY TEAMS" zh="我的队伍" />
    <section v-if="tasks.length">
      <h2 class="section-title">分给我的待办</h2>
      <div v-for="t in tasks" :key="t.id" class="task-row">
        <el-checkbox :aria-label="`标记任务「${t.title}」为已完成`" @change="finish(t)" />
        <span class="task-title">{{ t.title }}</span>
        <span class="muted small">
          <router-link :to="{ name: 'team', params: { id: t.teamId } }" class="team-name">{{ t.teamName }}</router-link>
          · {{ t.milestoneTitle }} · {{ t.allDay ? fmtDate(t.startAt) : fmtDateTime(t.startAt) }}
        </span>
      </div>
    </section>

    <section>
      <h2 class="section-title">我的队伍</h2>
      <el-empty v-if="!loading && teams.length === 0" :image-size="60">
        <template #description>还没有加入队伍，去<router-link to="/competitions">比赛库</router-link>里找找</template>
      </el-empty>
      <div class="grid">
        <TeamCard v-for="t in teams" :key="t.id" :team="t" show-competition />
      </div>
    </section>

    <section>
      <h2 class="section-title">收到的邀请</h2>
      <el-empty v-if="!loading && apps.received.length === 0" :image-size="60" description="还没有收到邀请" />
      <el-card v-for="a in pendingInvites" :key="a.id" shadow="never" class="item">
        <div class="item-main">
          <div>
            <router-link :to="{ name: 'team', params: { id: a.team.id } }" class="team-name">{{ a.team.name }}</router-link>
            <span class="muted"> 邀请你加入<template v-if="a.needRoleName">，负责{{ a.needRoleName }}</template></span>
            <div class="muted small">{{ teamLine(a) }} · {{ fmtDateTime(a.createdAt) }}</div>
            <div v-if="a.message" class="message">“{{ a.message }}”</div>
          </div>
          <div class="item-actions">
            <el-button type="primary" size="small" @click="act(() => acceptApplication(a.id), '已加入队伍')">接受</el-button>
            <el-button size="small" @click="act(() => rejectApplication(a.id), '已婉拒')">婉拒</el-button>
          </div>
        </div>
      </el-card>
      <div v-for="a in otherInvites" :key="a.id" class="history-row">
        <router-link :to="{ name: 'team', params: { id: a.team.id } }">{{ a.team.name }}</router-link>
        <span class="muted small">{{ teamLine(a) }}</span>
        <el-tag size="small" :type="APP_STATUS[a.status].type">{{ APP_STATUS[a.status].label }}</el-tag>
      </div>
    </section>

    <section>
      <h2 class="section-title">我发出的申请</h2>
      <el-empty v-if="!loading && apps.sent.length === 0" :image-size="60" description="还没有申请过队伍" />
      <div v-for="a in apps.sent" :key="a.id" class="history-row">
        <router-link :to="{ name: 'team', params: { id: a.team.id } }">{{ a.team.name }}</router-link>
        <span class="muted small">{{ teamLine(a) }}<template v-if="a.needRoleName"> · {{ a.needRoleName }}</template></span>
        <el-tag size="small" :type="APP_STATUS[a.status].type">{{ APP_STATUS[a.status].label }}</el-tag>
        <el-button
          v-if="a.status === 'PENDING'"
          text
          size="small"
          @click="act(() => withdrawApplication(a.id), '申请已撤回')"
        >
          撤回
        </el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 880px;
  margin: 0 auto;
  min-height: 200px;
}

section {
  margin-bottom: 28px;
}

.section-title {
  font-size: 17px;
  font-weight: 500;
  margin: 0 0 12px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.item {
  border-radius: 2px;
  margin-bottom: 10px;
}

.item-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.item-actions {
  flex: none;
}

.team-name {
  font-weight: 500;
}

.muted {
  color: var(--el-text-color-secondary);
}

.small {
  font-size: 13px;
  margin-top: 4px;
}

.message {
  margin-top: 6px;
  font-size: 13px;
}

.task-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.task-title {
  font-size: 14px;
}

.history-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
}

.history-row .small {
  margin-top: 0;
}
</style>

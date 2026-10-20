<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MatchSummary from '@/components/MatchSummary.vue'
import TeamFormDialog from '@/components/TeamFormDialog.vue'
import UserCard from '@/components/UserCard.vue'
import { suggestedGroups } from '@/api/survey'
import { createTeam, inviteUser } from '@/api/team'
import type { EditionDetail, GroupSuggestions, SuggestedGroup, TeamForm } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 批量撮合（只给建议）：还没组队的话，列出和哪些同样还没组队的同学一起组合适。
 * 系统不替人分组：点"按这个组合建队"才建队，并给组合里的人发邀请，对方同意了才算入队。
 */
const props = defineProps<{ edition: EditionDetail }>()

const router = useRouter()
const data = ref<GroupSuggestions | null>(null)
const loading = ref(true)
const picked = ref<SuggestedGroup | null>(null)
const creating = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await suggestedGroups(props.edition.id)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function pick(g: SuggestedGroup) {
  picked.value = g
  creating.value = true
}

/** 先建队，再给组合里的人逐个发邀请；有人邀请不了（比如刚加入了别的队）就提示，不影响建队 */
async function create(form: TeamForm) {
  const g = picked.value
  if (!g) return
  let teamId: number
  try {
    teamId = await createTeam(props.edition.id, form)
  } catch (e) {
    showError(e)
    throw e
  }
  const failed: string[] = []
  for (const m of g.members) {
    try {
      await inviteUser(teamId, m.id, null, '平台建议我们几个一起组队，看了看挺合适的，要一起参加吗？')
    } catch {
      failed.push(m.nickname)
    }
  }
  if (failed.length) ElMessage.warning(`队伍建好了，但没能邀请 ${failed.join('、')}（可能刚加入了别的队）`)
  else ElMessage.success(`队伍建好了，已经邀请 ${g.members.map((m) => m.nickname).join('、')}，等他们同意`)
  router.push({ name: 'team', params: { id: teamId } })
}

onMounted(load)
</script>

<template>
  <div v-if="edition.phase !== 'CLOSED' && edition.teamMax > 1 && !data?.currentTeamId" v-loading="loading" class="wrap">
    <h3 class="title">或者和这些同学一起组</h3>
    <p class="hint">
      从关注了这一届、还没组队的同学里，按问卷算出的几种组合，只是建议，组不组你自己决定。
      点"按这个组合建队"会先建队，再给他们发邀请，对方同意了才算入队。
    </p>
    <template v-if="data">
      <p v-if="data.note" class="note">{{ data.note }}</p>
      <div v-for="(g, i) in data.groups" :key="i" class="group">
        <div class="group-head">
          <span class="label">组合 {{ i + 1 }} · 加上你共 {{ g.members.length + 1 }} 人</span>
          <el-button size="small" type="primary" plain @click="pick(g)">按这个组合建队</el-button>
        </div>
        <div class="members">
          <UserCard v-for="m in g.members" :key="m.id" :user="m" :max-skills="3" />
        </div>
        <MatchSummary :match="g.match" compact />
      </div>
    </template>

    <TeamFormDialog
      v-model="creating"
      title="按这个组合建队"
      :team-min="edition.teamMin"
      :team-max="edition.teamMax"
      :initial="{ name: '', intro: '', maxMembers: edition.teamMax, meetingMode: 'MIXED' }"
      :submit="create"
    />
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
  margin: 0 0 6px;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.note {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.group {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 2px;
  margin-bottom: 10px;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.members {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
</style>

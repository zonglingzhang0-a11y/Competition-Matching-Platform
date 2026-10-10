<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import TeamCard from '@/components/TeamCard.vue'
import TeamFormDialog from '@/components/TeamFormDialog.vue'
import { createTeam, editionTeams } from '@/api/team'
import { useUserStore } from '@/stores/user'
import type { EditionDetail, TeamForm, TeamSummary } from '@/types/api'
import { showError } from '@/utils/feedback'

const props = defineProps<{ edition: EditionDetail }>()

const router = useRouter()
const user = useUserStore()

const teams = ref<TeamSummary[]>([])
const loading = ref(true)
const creating = ref(false)

/** 不能建队的原因；为空表示可以建 */
const createBlocked = computed(() => {
  if (props.edition.phase === 'CLOSED') return '这届比赛报名已截止'
  if (!user.isVerified) return '完成学校认证后才能建队'
  return ''
})

async function load() {
  loading.value = true
  try {
    teams.value = await editionTeams(props.edition.id)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function create(form: TeamForm) {
  try {
    const id = await createTeam(props.edition.id, form)
    ElMessage.success('队伍建好了，去添加招募需求吧')
    router.push({ name: 'team', params: { id } })
  } catch (e) {
    showError(e)
    throw e
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head">
      <h3 class="title">本届队伍 <span class="count">{{ teams.length }}</span></h3>
      <el-tooltip :content="createBlocked" :disabled="!createBlocked" placement="top">
        <span>
          <el-button type="primary" :icon="Plus" :disabled="!!createBlocked" @click="creating = true">创建队伍</el-button>
        </span>
      </el-tooltip>
    </div>
    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && teams.length === 0" :image-size="60" description="还没有人建队，你可以第一个" />
      <TeamCard v-for="t in teams" :key="t.id" :team="t" />
    </div>
    <TeamFormDialog
      v-model="creating"
      title="创建队伍"
      :team-min="edition.teamMin"
      :team-max="edition.teamMax"
      :submit="create"
    />
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24px 0 12px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin: 0;
}

.count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  min-height: 60px;
}
</style>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import FinishTeamDialog from '@/components/FinishTeamDialog.vue'
import JoinDialog from '@/components/JoinDialog.vue'
import NeedDialog from '@/components/NeedDialog.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import TeamFormDialog from '@/components/TeamFormDialog.vue'
import MatchSummary from '@/components/MatchSummary.vue'
import TeamTimeline from '@/components/TeamTimeline.vue'
import UserCard from '@/components/UserCard.vue'
import { getCompetition } from '@/api/competition'
import * as api from '@/api/team'
import { recommendedUsers } from '@/api/survey'
import { myRounds } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type {
  ApplicationView,
  NeedForm,
  NeedView,
  RecommendedUser,
  ReportTarget,
  RoundSummary,
  TeamDetail,
  TeamForm,
  UserBrief,
} from '@/types/api'
import { APP_STATUS, MEETING_MODE_LABEL, SKILL_LEVEL_LABEL, TEAM_STATUS } from '@/utils/labels'
import { fmtDate, fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const team = ref<TeamDetail | null>(null)
const loading = ref(true)
const roles = ref<string[]>([])
const applications = ref<ApplicationView[]>([])
/** 可以邀请的人，按匹配度排好序 */
const candidates = ref<RecommendedUser[]>([])

const editing = ref(false)
const needDialog = ref(false)
const editingNeed = ref<NeedView | null>(null)
const joinDialog = ref(false)
const inviteTarget = ref<UserBrief | null>(null)
const finishOpen = ref(false)
const reportOpen = ref(false)
const reportTarget = ref<{ type: ReportTarget; id: number; name: string }>({ type: 'TEAM', id: 0, name: '' })
/** 比赛结束后，这支队的互评（我是队员时才有） */
const round = ref<RoundSummary | null>(null)

const isCaptain = computed(() => team.value?.me.captain ?? false)
const finished = computed(() => team.value?.status === 'FINISHED')
const myId = computed(() => userStore.me?.id)
const pendingApplies = computed(() =>
  applications.value.filter((a) => a.status === 'PENDING' && a.direction === 'APPLY'),
)
const pendingInvites = computed(() =>
  applications.value.filter((a) => a.status === 'PENDING' && a.direction === 'INVITE'),
)
const handled = computed(() => applications.value.filter((a) => a.status !== 'PENDING'))
const editForm = computed<TeamForm | undefined>(() =>
  team.value
    ? {
        name: team.value.name,
        intro: team.value.intro ?? '',
        maxMembers: team.value.maxMembers,
        meetingMode: team.value.meetingMode,
      }
    : undefined,
)

async function load() {
  loading.value = true
  try {
    const t = await api.getTeam(Number(route.params.id))
    team.value = t
    if (t.me.captain) {
      ;[applications.value, candidates.value] = await Promise.all([api.teamApplications(t.id), recommendedUsers(t.id)])
    }
    round.value =
      t.status === 'FINISHED' && t.me.member ? ((await myRounds()).find((r) => r.teamId === t.id) ?? null) : null
    if (roles.value.length === 0) {
      roles.value = (await getCompetition(t.edition.competitionId)).roles
    }
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

/** 执行一个操作：成功就提示并刷新，失败就提示原因。confirm 给了就先确认 */
async function run(action: () => Promise<unknown>, success: string, confirm?: string) {
  if (confirm) {
    try {
      await ElMessageBox.confirm(confirm, '请确认', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    } catch {
      return false
    }
  }
  try {
    await action()
    ElMessage.success(success)
    await load()
    return true
  } catch (e) {
    showError(e)
    return false
  }
}

/** 给弹窗用：失败时抛出，让弹窗保持打开 */
async function submitOrThrow(action: () => Promise<unknown>, success: string) {
  try {
    await action()
  } catch (e) {
    showError(e)
    throw e
  }
  ElMessage.success(success)
  await load()
}

const id = () => team.value!.id

function saveTeam(form: TeamForm) {
  return submitOrThrow(() => api.updateTeam(id(), form), '队伍信息已保存')
}

function openNeed(need: NeedView | null) {
  editingNeed.value = need
  needDialog.value = true
}

function saveNeed(form: NeedForm) {
  const need = editingNeed.value
  return submitOrThrow(
    () => (need ? api.updateNeed(need.id, form) : api.addNeed(id(), form)),
    need ? '招募需求已修改' : '招募需求已添加',
  )
}

function openApply() {
  inviteTarget.value = null
  joinDialog.value = true
}

function openInvite(user: UserBrief) {
  inviteTarget.value = user
  joinDialog.value = true
}

function submitJoin(needId: number | null, message: string) {
  const target = inviteTarget.value
  return target
    ? submitOrThrow(() => api.inviteUser(id(), target.id, needId, message), `已邀请 ${target.nickname}`)
    : submitOrThrow(() => api.applyTeam(id(), needId, message), '申请已发出，等队长处理')
}

async function leave() {
  const ok = await run(() => api.leaveTeam(id()), '已退出队伍', `确定退出「${team.value!.name}」吗？`)
  if (ok) router.push({ name: 'my-teams' })
}

async function disband() {
  const ok = await run(
    () => api.disbandTeam(id()),
    '队伍已解散',
    `解散后所有队员都会退出「${team.value!.name}」，而且不能恢复。确定解散吗？`,
  )
  if (ok) router.push({ name: 'my-teams' })
}

// 模板里用到的操作。包成具名函数，模板里不直接调接口
type Member = TeamDetail['members'][number]
const pendingInvitationId = () => team.value!.me.pendingInvitation!.id
const pendingApplicationId = () => team.value!.me.pendingApplication!.id

const stopRecruiting = () =>
  run(() => api.setRecruiting(id(), false), '已停止招募', '停止招募后，还没处理的申请和邀请都会失效。确定吗？')
const reopenRecruiting = () => run(() => api.setRecruiting(id(), true), '已重新开放招募')
const acceptInvitation = () => run(() => api.acceptApplication(pendingInvitationId()), '已加入队伍')
const rejectInvitation = () => run(() => api.rejectApplication(pendingInvitationId()), '已婉拒')
const withdrawMyApplication = () => run(() => api.withdrawApplication(pendingApplicationId()), '申请已撤回')
const makeCaptain = (m: Member) =>
  run(() => api.transferCaptain(id(), m.user.id), '队长已转让', `把队长转给 ${m.user.nickname} 吗？转让后你就是普通队员了。`)
const removeFromTeam = (m: Member) =>
  run(() => api.removeMember(id(), m.user.id), '已移出队伍', `把 ${m.user.nickname} 移出队伍吗？`)
const cancelNeed = (n: NeedView) =>
  run(() => api.cancelNeed(n.id), '已取消这条需求', `取消「${n.roleName}」这条招募需求吗？`)
const acceptApply = (a: ApplicationView) => run(() => api.acceptApplication(a.id), `${a.user.nickname} 已加入队伍`)
const rejectApply = (a: ApplicationView) => run(() => api.rejectApplication(a.id), '已婉拒')
const withdrawInvite = (a: ApplicationView) => run(() => api.withdrawApplication(a.id), '邀请已撤回')

function report(type: ReportTarget, targetId: number, name: string) {
  reportTarget.value = { type, id: targetId, name }
  reportOpen.value = true
}

function goReview() {
  if (round.value) router.push({ name: 'review-form', params: { id: round.value.id } })
}

function contactText(m: Member): string {
  const c = m.contact
  if (!c) return ''
  const parts = [c.realName && `姓名 ${c.realName}`, c.wechat && `微信 ${c.wechat}`, c.qq && `QQ ${c.qq}`]
  return parts.filter(Boolean).join(' · ') || '还没填联系方式'
}

watch(() => route.params.id, load, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="page">
    <el-button
      v-if="team"
      text
      :icon="ArrowLeft"
      class="back"
      @click="router.push({ name: 'competition', params: { id: team.edition.competitionId } })"
    >
      {{ team.edition.competitionName }}
    </el-button>

    <template v-if="team">
      <!-- 队伍信息和主要操作 -->
      <el-card shadow="never" class="block">
        <div class="head">
          <div class="head-main">
            <h1 class="title">
              {{ team.name }}
              <el-tag :type="TEAM_STATUS[team.status].type">{{ TEAM_STATUS[team.status].label }}</el-tag>
              <el-tag v-if="team.award" type="warning" effect="plain">{{ team.award }}</el-tag>
            </h1>
            <div class="meta">
              {{ team.edition.editionTitle }} · {{ team.memberCount }}/{{ team.maxMembers }} 人 ·
              {{ MEETING_MODE_LABEL[team.meetingMode] }} · {{ fmtDate(team.createdAt) }} 建队
            </div>
          </div>
          <div class="actions">
            <template v-if="isCaptain && !finished">
              <el-button type="primary" plain @click="finishOpen = true">比赛结束</el-button>
              <el-button @click="editing = true">编辑信息</el-button>
              <el-button
                v-if="team.status === 'RECRUITING' || team.status === 'FULL'"
                @click="stopRecruiting"
              >
                停止招募
              </el-button>
              <el-button v-else-if="team.status === 'CLOSED'" @click="reopenRecruiting">
                重新开放招募
              </el-button>
              <el-button type="danger" plain @click="disband">解散</el-button>
            </template>
            <el-button v-else-if="team.me.member && !finished" @click="leave">退出队伍</el-button>
            <el-button v-if="!team.me.member" text @click="report('TEAM', team.id, team.name)">举报</el-button>
          </div>
        </div>
        <p v-if="team.intro" class="intro">{{ team.intro }}</p>

        <template v-if="round">
          <el-alert
            v-if="round.status === 'OPEN' && !round.submitted"
            type="warning"
            :closable="false"
            show-icon
            class="relation"
            :title="`比赛结束了，给队友做个互评吧（${fmtDateTime(round.closesAt)} 截止）`"
          >
            每位队友 6 个问题，一分钟左右。所有人交齐或到截止时间后统一揭晓，看不到别人具体给你打了什么分。
            <div class="relation-actions">
              <el-button type="primary" size="small" @click="goReview">去互评</el-button>
            </div>
          </el-alert>
          <el-alert
            v-else-if="round.status === 'OPEN'"
            type="info"
            :closable="false"
            show-icon
            class="relation"
            :title="`你已经交了互评，${round.raters}/${round.participants} 人已交`"
          >
            所有人交齐或 {{ fmtDateTime(round.closesAt) }} 截止后揭晓，在那之前还可以修改。
            <div class="relation-actions">
              <el-button size="small" @click="goReview">修改</el-button>
            </div>
          </el-alert>
          <el-alert v-else type="success" :closable="false" show-icon class="relation" title="互评已经揭晓，结果计入了信用分">
            <div class="relation-actions">
              <el-button size="small" @click="router.push({ name: 'reviews' })">看我的信用</el-button>
            </div>
          </el-alert>
        </template>

        <template v-if="!team.me.member">
          <el-alert v-if="team.me.pendingInvitation" type="success" :closable="false" class="relation">
            <template #title>队长邀请你加入这支队伍</template>
            <div class="relation-actions">
              <el-button type="primary" size="small" @click="acceptInvitation">
                接受邀请
              </el-button>
              <el-button size="small" @click="rejectInvitation">
                婉拒
              </el-button>
            </div>
          </el-alert>
          <el-alert v-else-if="team.me.pendingApplication" type="info" :closable="false" class="relation">
            <template #title>
              你在 {{ fmtDateTime(team.me.pendingApplication.createdAt) }} 申请了加入，等队长处理
            </template>
            <div class="relation-actions">
              <el-button size="small" @click="withdrawMyApplication">
                撤回申请
              </el-button>
            </div>
          </el-alert>
          <div v-else class="apply">
            <el-button type="primary" :disabled="!!team.me.applyBlockedReason" @click="openApply">申请加入</el-button>
            <span v-if="team.me.applyBlockedReason" class="blocked">{{ team.me.applyBlockedReason }}</span>
          </div>
        </template>
      </el-card>

      <!-- 队员 -->
      <el-card shadow="never" class="block">
        <template #header>队员（{{ team.memberCount }}/{{ team.maxMembers }}）</template>
        <div v-for="m in team.members" :key="m.user.id" class="row">
          <UserCard :user="m.user">
            <template #badge>
              <el-tag v-if="m.captain" size="small" type="warning">队长</el-tag>
              <el-tag v-if="m.roleName" size="small" effect="plain">{{ m.roleName }}</el-tag>
            </template>
            <div v-if="team.me.member" class="contact">{{ contactText(m) }}</div>
          </UserCard>
          <div v-if="isCaptain && !m.captain && !finished" class="row-actions">
            <el-button
              text
              size="small"
              @click="makeCaptain(m)"
            >
              设为队长
            </el-button>
            <el-button
              text
              size="small"
              type="danger"
              @click="removeFromTeam(m)"
            >
              移出
            </el-button>
          </div>
          <el-button
            v-else-if="m.user.id !== myId"
            text
            size="small"
            class="report-member"
            @click="report('USER', m.user.id, m.user.nickname)"
          >
            举报
          </el-button>
        </div>
        <p v-if="!team.me.member" class="hint">加入后可以看到队友的联系方式。</p>
      </el-card>

      <!-- 招募需求 -->
      <el-card shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span>招募需求</span>
            <el-button v-if="isCaptain && !finished" size="small" :icon="Plus" @click="openNeed(null)">添加需求</el-button>
          </div>
        </template>
        <el-empty
          v-if="team.needs.length === 0"
          :image-size="60"
          :description="isCaptain ? '写清楚缺什么人、要什么技能，系统才能帮你推荐' : '队长还没写招募需求'"
        />
        <div v-for="n in team.needs" :key="n.id" class="row need">
          <div>
            <div class="need-title">
              {{ n.roleName }}
              <span class="need-count">已招 {{ n.filled }}/{{ n.headcount }}</span>
              <el-tag v-if="n.status === 'FILLED'" size="small" type="info">已招满</el-tag>
            </div>
            <div v-if="n.skills.length" class="need-skills">
              <el-tag v-for="s in n.skills" :key="s.skillId" size="small" effect="plain">
                {{ s.name }} ≥ {{ SKILL_LEVEL_LABEL[s.minLevel] }}
              </el-tag>
            </div>
            <div v-if="n.note" class="need-note">{{ n.note }}</div>
          </div>
          <div v-if="isCaptain && !finished" class="row-actions">
            <el-button text size="small" @click="openNeed(n)">修改</el-button>
            <el-button
              text
              size="small"
              type="danger"
              @click="cancelNeed(n)"
            >
              取消
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 时间线：官方节点 + 队伍里程碑 -->
      <el-card shadow="never" class="block">
        <TeamTimeline :team-id="team.id" />
      </el-card>

      <!-- 队长：处理申请 -->
      <el-card v-if="isCaptain" shadow="never" class="block">
        <template #header>入队申请</template>
        <el-empty v-if="pendingApplies.length === 0" :image-size="60" description="暂时没有待处理的申请" />
        <div v-for="a in pendingApplies" :key="a.id" class="row">
          <UserCard :user="a.user">
            <template #badge>
              <el-tag v-if="a.needRoleName" size="small" effect="plain">申请：{{ a.needRoleName }}</el-tag>
            </template>
            <div v-if="a.message" class="message">“{{ a.message }}”</div>
            <div class="time">{{ fmtDateTime(a.createdAt) }}</div>
          </UserCard>
          <div class="row-actions">
            <el-button type="primary" size="small" @click="acceptApply(a)">
              同意
            </el-button>
            <el-button size="small" @click="rejectApply(a)">婉拒</el-button>
          </div>
        </div>

        <template v-if="pendingInvites.length">
          <h4 class="sub">已发出的邀请，等对方回复</h4>
          <div v-for="a in pendingInvites" :key="a.id" class="row">
            <UserCard :user="a.user" :max-skills="3" />
            <div class="row-actions">
              <el-button text size="small" @click="withdrawInvite(a)">撤回</el-button>
            </div>
          </div>
        </template>

        <el-collapse v-if="handled.length" class="history">
          <el-collapse-item :title="`处理记录（${handled.length}）`">
            <div v-for="a in handled" :key="a.id" class="history-row">
              <span>{{ a.direction === 'APPLY' ? '申请' : '邀请' }}</span>
              <span class="history-name">{{ a.user.nickname }}</span>
              <el-tag size="small" :type="APP_STATUS[a.status].type">{{ APP_STATUS[a.status].label }}</el-tag>
              <span class="time">{{ fmtDateTime(a.handledAt ?? a.createdAt) }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <!-- 队长：可以邀请的人 -->
      <el-card v-if="isCaptain && team.status === 'RECRUITING'" shadow="never" class="block">
        <template #header>推荐邀请的人</template>
        <p class="hint top">关注了这届比赛、已经认证、还没组队的同学，按和你们队的匹配度排序。招募需求写得越清楚，推荐越准。</p>
        <el-empty v-if="candidates.length === 0" :image-size="60" description="暂时没有找队伍的人，过几天再来看看" />
        <div v-for="c in candidates" :key="c.user.id" class="row candidate" :class="{ vetoed: c.match.veto }">
          <div class="candidate-main">
            <UserCard :user="c.user" />
            <MatchSummary :match="c.match" class="candidate-match" />
          </div>
          <div class="row-actions">
            <template v-if="!c.match.veto">
              <el-button v-if="!c.invited" type="primary" plain size="small" @click="openInvite(c.user)">邀请</el-button>
              <span v-else class="invited">已邀请</span>
            </template>
          </div>
        </div>
      </el-card>

      <TeamFormDialog
        v-model="editing"
        title="编辑队伍信息"
        :initial="editForm"
        :team-min="Math.max(team.edition.teamMin, team.memberCount)"
        :team-max="team.edition.teamMax"
        :submit="saveTeam"
      />
      <NeedDialog v-model="needDialog" :need="editingNeed" :roles="roles" :submit="saveNeed" />
      <FinishTeamDialog
        v-model="finishOpen"
        :team-id="team.id"
        :team-name="team.name"
        :member-count="team.memberCount"
        @finished="load"
      />
      <ReportDialog
        v-model="reportOpen"
        :target-type="reportTarget.type"
        :target-id="reportTarget.id"
        :target-name="reportTarget.name"
      />
      <JoinDialog
        v-model="joinDialog"
        :title="inviteTarget ? '邀请加入' : '申请加入'"
        :target="inviteTarget?.nickname"
        :needs="team.needs"
        :submit="submitJoin"
      />
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
  border-radius: 2px;
  margin-bottom: 16px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.title {
  font-size: 22px;
  font-weight: 500;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.actions .el-button + .el-button {
  margin-left: 0;
}

.intro {
  margin: 14px 0 0;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
}

.relation {
  margin-top: 16px;
}

.relation-actions {
  margin-top: 8px;
}

.apply {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.blocked {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.row:last-of-type {
  border-bottom: none;
}

.row-actions {
  flex: none;
  display: flex;
  gap: 4px;
}

.contact {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.need-title {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.need-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.need-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.need-note,
.message {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sub {
  font-size: 13px;
  font-weight: 500;
  margin: 16px 0 0;
  color: var(--el-text-color-regular);
}

.history {
  margin-top: 12px;
}

.history-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 4px 0;
}

.history-name {
  font-weight: 500;
}

.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 12px 0 0;
}

.hint.top {
  margin: 0 0 4px;
}

.candidate-main {
  min-width: 0;
  flex: 1;
}

.candidate-match {
  margin-top: 10px;
}

.vetoed {
  opacity: 0.6;
}

.report-member {
  color: var(--el-text-color-secondary);
}

.invited {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

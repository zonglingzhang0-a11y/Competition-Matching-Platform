<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MatchSummary from '@/components/MatchSummary.vue'
import UserName from '@/components/UserName.vue'
import { acceptApplication, rejectApplication } from '@/api/team'
import { markRead } from '@/api/notification'
import { useUserPageStore } from '@/stores/userPage'
import type { HomePending, HomeRecommendation, HomeTeammate, Notification } from '@/types/api'
import { notificationRoute } from '@/utils/notificationLink'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 主页右栏：只放需要你决定的事。待你处理（邀请、申请，附上处理后的影响）、推荐队友（或推荐队伍）、最近的消息。
 */
const props = defineProps<{
  pending: HomePending[]
  teammates: HomeTeammate[]
  recommendations: HomeRecommendation[]
  messages: Notification[]
}>()
const emit = defineEmits<{ changed: [] }>()

const router = useRouter()
const userPage = useUserPageStore()
const busy = ref<number | null>(null)

/** 没有当队长要招人的队时，改为推荐可以加入的队伍（每个关注的比赛取最合适的一支） */
const teams = computed(() =>
  props.recommendations
    .flatMap((r) => r.teams.slice(0, 1).map((t) => ({ ...t, edition: r.edition })))
    .slice(0, 3),
)

/** 影响里带"同时进行""不能""先退出"的是提醒，标红 */
const risky = (p: HomePending) => !!p.consequence && /同时进行|不能|先退出|满员/.test(p.consequence)

async function act(p: HomePending, accept: boolean) {
  busy.value = p.applicationId
  try {
    if (accept) await acceptApplication(p.applicationId)
    else await rejectApplication(p.applicationId)
    ElMessage.success(accept ? (p.kind === 'INVITE' ? `已加入「${p.teamName}」` : '已同意') : '已婉拒')
    emit('changed')
  } catch (e) {
    showError(e)
  } finally {
    busy.value = null
  }
}

function openMessage(n: Notification) {
  if (!n.readAt) markRead(n.id).catch(() => {})
  const to = notificationRoute(n)
  router.push(to ?? { name: 'notifications' })
}
</script>

<template>
  <div class="col">
    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="kicker">ACTION <span class="zh">/ 需要你决定</span></div>
          <h2 class="panel-title">待你处理 <span v-if="pending.length" class="badge num">{{ pending.length }}</span></h2>
        </div>
      </div>
      <p v-if="pending.length === 0" class="empty">没有要你处理的邀请和申请。</p>
      <article v-for="p in pending" :key="p.applicationId" class="pending">
        <div class="pending-head">
          <span class="tag">{{ p.kind === 'INVITE' ? '邀请' : '申请' }}</span>
          <span class="pending-title">
            <template v-if="p.kind === 'INVITE'">
              「<router-link :to="{ name: 'team', params: { id: p.teamId } }">{{ p.teamName }}</router-link>」邀请你加入
            </template>
            <template v-else>
              <UserName v-if="p.whoId !== null && p.who" :id="p.whoId" :name="p.who" /><template v-else>{{ p.who }}</template> 申请加入「<router-link :to="{ name: 'team', params: { id: p.teamId } }">{{ p.teamName }}</router-link>」
            </template>
            <template v-if="p.roleName">，担任{{ p.roleName }}</template>
          </span>
        </div>
        <div class="sub">{{ p.competitionName }}<template v-if="p.kind === 'INVITE' && p.who"> · 队长 <UserName v-if="p.whoId !== null" :id="p.whoId" :name="p.who" /><template v-else>{{ p.who }}</template></template> · {{ fmtDateTime(p.createdAt) }}</div>
        <p v-if="p.message" class="quote">“{{ p.message }}”</p>
        <p v-if="p.consequence" class="consequence" :class="{ risky: risky(p) }">{{ p.consequence }}</p>
        <div class="buttons">
          <el-button type="primary" size="small" :loading="busy === p.applicationId" @click="act(p, true)">
            {{ p.kind === 'INVITE' ? '接受' : '同意' }}
          </el-button>
          <el-button size="small" @click="router.push({ name: 'team', params: { id: p.teamId } })">看队伍</el-button>
          <el-button size="small" text :disabled="busy === p.applicationId" @click="act(p, false)">婉拒</el-button>
        </div>
      </article>
    </section>

    <section v-if="teammates.length" class="panel">
      <div class="panel-head">
        <div>
          <div class="kicker">RECOMMENDED <span class="zh">/ 推荐队友</span></div>
          <h2 class="panel-title">推荐队友 <span class="count">为「{{ teammates[0]!.teamName }}」</span></h2>
        </div>
      </div>
      <div v-for="t in teammates" :key="t.teamId + '-' + t.userId" class="person">
        <button type="button" class="avatar" :aria-label="`查看 ${t.nickname} 的资料`" @click="userPage.open(t.userId)">
          {{ t.nickname.slice(0, 1) }}
        </button>
        <div class="person-text">
          <strong><UserName :id="t.userId" :name="t.nickname" /></strong>
          <span class="sub">{{ [t.major, t.enrollYear ? `${t.enrollYear} 级` : null].filter(Boolean).join(' · ') }}</span>
          <span v-if="t.reason" class="sub">{{ t.reason }}</span>
        </div>
        <span class="score num" :title="'匹配分'">{{ Math.round(t.score) }}</span>
        <router-link :to="{ name: 'team', params: { id: t.teamId } }" class="invite">
          {{ t.invited ? '已邀请' : '去邀请' }}
        </router-link>
      </div>
    </section>

    <section v-else-if="teams.length" class="panel">
      <div class="panel-head">
        <div>
          <div class="kicker">RECOMMENDED <span class="zh">/ 推荐队伍</span></div>
          <h2 class="panel-title">为你推荐的队伍</h2>
        </div>
      </div>
      <div v-for="t in teams" :key="t.team.id" class="rec">
        <router-link :to="{ name: 'team', params: { id: t.team.id } }" class="rec-name">{{ t.team.name }}</router-link>
        <span class="sub">
          {{ t.edition.competitionName }} · {{ t.team.memberCount }}/{{ t.team.maxMembers }} 人
          <template v-if="t.team.openNeeds.length"> · 在招 {{ t.team.openNeeds.map((n) => n.roleName).join('、') }}</template>
        </span>
        <MatchSummary :match="t.match" compact />
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="kicker">MESSAGES <span class="zh">/ 消息</span></div>
          <h2 class="panel-title">消息</h2>
        </div>
        <router-link to="/notifications" class="more">全部 →</router-link>
      </div>
      <p v-if="messages.length === 0" class="empty">还没有消息。</p>
      <button v-for="n in messages" :key="n.id" type="button" class="msg" :class="{ unread: !n.readAt }" @click="openMessage(n)">
        <span class="msg-dot" aria-hidden="true" />
        <span class="msg-text">
          <strong>{{ n.title }}</strong>
          <span class="sub">{{ n.content }}</span>
        </span>
        <span class="msg-time num">{{ fmtDateTime(n.createdAt) }}</span>
      </button>
    </section>
  </div>
</template>

<style scoped>
.col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.badge {
  display: inline-block;
  min-width: 18px;
  margin-left: 6px;
  padding: 0 5px;
  background: var(--jd-red);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  vertical-align: 2px;
}

.pending {
  padding: 12px;
  margin-top: 8px;
  background: var(--jd-overlay);
  border: 1px solid var(--jd-line-lighter);
}

.pending-head {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.tag {
  flex: none;
  padding: 0 5px;
  border: 1px solid var(--jd-ink);
  font-size: 11px;
  font-weight: 700;
}

.pending-title {
  font-size: 13px;
  font-weight: 700;
}

.sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--jd-muted);
}

.quote {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--jd-ink-2);
}

.consequence {
  margin: 6px 0 0;
  padding: 4px 8px;
  border-left: 2px solid var(--jd-line-strong);
  font-size: 12px;
  color: var(--jd-ink-2);
}

.consequence.risky {
  border-left-color: var(--jd-red);
  background: var(--jd-red-soft);
  color: var(--jd-red);
}

.buttons {
  display: flex;
  gap: 4px;
  margin-top: 10px;
}

.person,
.rec {
  padding: 10px 0;
  border-top: 1px solid var(--jd-line-lighter);
}

.person {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  background: var(--jd-ink-2);
  color: var(--jd-paper);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.avatar:hover {
  background: var(--jd-accent);
}

.person-text {
  flex: 1;
  min-width: 0;
}

.score {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 2px solid var(--jd-accent);
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
}

.invite {
  flex: none;
  padding: 3px 8px;
  background: var(--jd-ink);
  color: var(--jd-paper);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.rec-name {
  font-weight: 700;
  text-decoration: none;
}

.msg {
  display: flex;
  gap: 8px;
  width: 100%;
  padding: 8px 0;
  border: 0;
  border-top: 1px solid var(--jd-line-lighter);
  background: none;
  font: inherit;
  text-align: left;
  color: var(--jd-ink);
  cursor: pointer;
}

.msg:hover strong {
  color: var(--jd-accent);
}

.msg-dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 6px;
  background: transparent;
}

.msg.unread .msg-dot {
  background: var(--jd-red);
}

.msg-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.msg-text .sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-time {
  flex: none;
  font-size: 11px;
  color: var(--jd-muted);
}
</style>

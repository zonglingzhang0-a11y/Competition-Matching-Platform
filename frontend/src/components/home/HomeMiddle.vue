<script setup lang="ts">
import type { MyCompetition, Suggestion } from '@/types/api'
import { LEVEL_LABEL } from '@/utils/labels'
import { fmtDate } from '@/utils/time'

/** 主页中栏：我参加的比赛（阶段进度）、适合你报名的比赛（官网、报名入口、找队伍） */
defineProps<{ mine: MyCompetition[]; suggestions: Suggestion[] }>()

const soon = (days: number | null | undefined) => days !== null && days !== undefined && days <= 3
</script>

<template>
  <div class="col">
    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="kicker">MY COMPETITIONS <span class="zh">/ 我参加的比赛</span></div>
          <h2 class="panel-title">我参加的比赛 <span class="count num">{{ mine.length }}</span></h2>
        </div>
        <router-link to="/teams" class="more">全部队伍 →</router-link>
      </div>

      <p v-if="mine.length === 0" class="empty">还没参加比赛。下面这些正在报名，找一支队伍或者自己建一支。</p>

      <article v-for="m in mine" :key="m.teamId" class="comp">
        <div class="comp-top">
          <router-link :to="{ name: 'competition', params: { id: m.competitionId } }" class="comp-name">
            {{ m.competitionName }}
          </router-link>
          <el-tag v-if="m.level" size="small" effect="plain">{{ LEVEL_LABEL[m.level] }}</el-tag>
          <span v-if="m.nextDeadline" class="due" :class="{ deadline: soon(m.nextDeadline.daysLeft) }">
            {{ m.nextDeadline.title }}{{ m.nextDeadline.daysLeft <= 0 ? '就在今天' : `还有 ${m.nextDeadline.daysLeft} 天` }}
          </span>
        </div>

        <ol v-if="m.stages.length" class="stages">
          <li v-for="s in m.stages" :key="s.title + s.at" :class="s.state.toLowerCase()">
            <span class="dot" aria-hidden="true" />
            <span class="stage-title">{{ s.title }}</span>
            <span class="stage-date num">{{ fmtDate(s.at) }}</span>
          </li>
        </ol>

        <div class="comp-bottom">
          <span class="avatars" :aria-label="`队员：${m.members.join('、')}`">
            <span v-for="name in m.members" :key="name" class="avatar" :title="name">{{ name.slice(0, 1) }}</span>
          </span>
          <span class="meta">
            「{{ m.teamName }}」{{ m.memberCount }}/{{ m.maxMembers }} 人
            <template v-if="m.openNeeds"> · 还缺 {{ m.openNeeds }} 个角色</template>
          </span>
          <span class="actions">
            <a v-if="m.officialUrl" :href="m.officialUrl" target="_blank" rel="noopener noreferrer" class="link">官网 ↗</a>
            <router-link :to="{ name: 'team', params: { id: m.teamId } }" class="link strong">队伍空间 →</router-link>
          </span>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="kicker">OPEN FOR SIGN-UP <span class="zh">/ 适合你报名</span></div>
          <h2 class="panel-title">适合你报名</h2>
        </div>
        <router-link to="/competitions" class="more">比赛库 →</router-link>
      </div>

      <p v-if="suggestions.length === 0" class="empty">现在没有正在报名、你还没参加的比赛。</p>

      <article v-for="s in suggestions" :key="s.editionId" class="sug">
        <div class="sug-top">
          <router-link :to="{ name: 'competition', params: { id: s.competitionId } }" class="comp-name">
            {{ s.competitionName }}
          </router-link>
          <el-tag v-if="s.level" size="small" effect="plain">{{ LEVEL_LABEL[s.level] }}</el-tag>
          <span v-if="s.daysLeft !== null" class="due" :class="{ deadline: soon(s.daysLeft) }">
            报名截止{{ s.daysLeft <= 0 ? '就在今天' : `还有 ${s.daysLeft} 天` }}
          </span>
        </div>
        <div class="sug-bottom">
          <span class="meta">
            {{ s.editionTitle }} ·
            {{ s.solo ? '个人赛，无需组队' : s.recruitingTeams ? `${s.recruitingTeams} 支队在招人` : '还没有队伍在招人' }}
            <template v-if="s.followed"> · 已关注</template>
          </span>
          <span class="actions">
            <a v-if="s.officialUrl" :href="s.officialUrl" target="_blank" rel="noopener noreferrer" class="link">官网 ↗</a>
            <a v-if="s.noticeUrl" :href="s.noticeUrl" target="_blank" rel="noopener noreferrer" class="link">报名通知 ↗</a>
            <router-link v-if="!s.solo" :to="{ name: 'competition', params: { id: s.competitionId } }" class="find">
              找队伍
            </router-link>
          </span>
        </div>
      </article>
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

.comp,
.sug {
  padding: 12px 0;
  border-top: 1px solid var(--jd-line-lighter);
}

.comp-top,
.sug-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.comp-name {
  font-weight: 700;
  text-decoration: none;
}

.due {
  margin-left: auto;
  font-size: 12px;
  color: var(--jd-ink-2);
  white-space: nowrap;
}

.due.deadline {
  color: var(--jd-red);
  font-weight: 700;
}

/* 阶段：圆点连线，过了的实心，当前的金色描边，没到的空心 */
.stages {
  display: flex;
  margin: 12px 0 10px;
  padding: 0;
  list-style: none;
}

.stages li {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.stages li + li::before {
  content: '';
  position: absolute;
  top: 5px;
  right: 50%;
  width: 100%;
  border-top: 1px solid var(--jd-line);
}

.stages li.done + li::before,
.stages li.done + li.current::before {
  border-top-color: var(--jd-ink);
}

.dot {
  position: relative;
  z-index: 1;
  width: 11px;
  height: 11px;
  border: 1px solid var(--jd-line-strong);
  border-radius: 50%;
  background: var(--jd-panel);
}

.done .dot {
  border-color: var(--jd-ink);
  background: var(--jd-ink);
}

.current .dot {
  border: 2px solid var(--jd-accent);
  background: var(--jd-panel);
}

.stage-title {
  max-width: 100%;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--jd-muted);
}

.current .stage-title {
  color: var(--jd-ink);
  font-weight: 700;
}

.stage-date {
  font-size: 11px;
  color: var(--jd-muted);
}

.comp-bottom,
.sug-bottom {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.avatars {
  display: inline-flex;
}

.avatar {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  margin-right: -4px;
  border: 1px solid var(--jd-panel);
  background: var(--jd-ink-2);
  color: var(--jd-paper);
  font-size: 11px;
  font-weight: 700;
}

.meta {
  margin-left: 6px;
  font-size: 12px;
  color: var(--jd-muted);
}

.sug-bottom .meta {
  margin-left: 0;
}

.actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.link {
  font-size: 12px;
  color: var(--jd-ink-2);
  text-decoration: none;
}

.link:hover {
  color: var(--jd-accent);
}

.link.strong {
  color: var(--jd-ink);
  font-weight: 700;
}

.find {
  padding: 3px 10px;
  background: var(--jd-ink);
  color: var(--jd-paper);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.find:hover {
  background: #2e2e29;
  color: var(--jd-paper);
}
</style>

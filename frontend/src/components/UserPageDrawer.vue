<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Close, Lock } from '@element-plus/icons-vue'
import { getUserPage } from '@/api/profile'
import SkillRadar from '@/components/SkillRadar.vue'
import RadarSkillGroups from '@/components/RadarSkillGroups.vue'
import { useUserPageStore } from '@/stores/userPage'
import type { PageExperience, UserPage } from '@/types/api'
import { DEGREES, TEAM_RECORD_STATE, creditType } from '@/utils/labels'
import { fmtDate } from '@/utils/time'
import { safeUrl } from '@/utils/url'

/**
 * 个人资料页抽屉：点开任何人的名字，从右边滑出来，不用离开当前页面。
 * 参考游戏平台的个人资料：最高荣誉、生涯数据、像游戏库一样的组队记录（附在队天数）、两人的共同点、隐私提示。
 * 能看到哪些部分由后端决定（见后端 UserPageAccess），看不到的字段为空，这里只按有没有数据来显示。
 */
const store = useUserPageStore()

/** 手机上铺满整屏，不然 500px 宽的抽屉左边会被切掉 */
const narrowQuery = window.matchMedia('(max-width: 560px)')
const narrow = ref(narrowQuery.matches)
const onNarrow = (e: MediaQueryListEvent) => (narrow.value = e.matches)
narrowQuery.addEventListener('change', onNarrow)
onBeforeUnmount(() => narrowQuery.removeEventListener('change', onNarrow))
const route = useRoute()

const page = ref<UserPage | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  if (!store.visible || store.userId === null) return
  loading.value = true
  error.value = ''
  try {
    page.value = await getUserPage(store.userId, store.preview)
  } catch (e) {
    page.value = null
    error.value = e instanceof Error ? e.message : '资料加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => [store.visible, store.userId, store.preview], load)
// 在抽屉里点了队伍的链接，页面跳走了，抽屉也跟着关上
watch(() => route.fullPath, () => store.close())

/** 看的是自己（包括正在预览别人看到的样子） */
const isOwner = computed(() => page.value?.access.level === 'SELF' || page.value?.access.preview === true)

const subtitle = computed(() => {
  const p = page.value
  if (!p) return ''
  const degree = DEGREES.find((d) => d.value === p.degree)?.label
  return [p.schoolName, p.major, p.enrollYear ? `${p.enrollYear} 级` : null, degree].filter(Boolean).join(' · ')
})

const hasMutual = computed(() => {
  const m = page.value?.mutual
  return !!m && (m.sameSchool || m.editions.length > 0 || m.teams.length > 0)
})

const hasAbout = computed(() => !!(page.value?.bio || page.value?.selfIntro || page.value?.weakness))

/** 来源标记：平台记录最可信，其次是核验过的，最后是自己填的 */
function sourceOf(e: PageExperience): { label: string; type: 'success' | 'primary' | 'info' } {
  if (e.source === 'PLATFORM') return { label: '平台记录', type: 'success' }
  if (e.verified) return { label: '已核验', type: 'primary' }
  return { label: '自己填写', type: 'info' }
}

/** 证明链接是用户自己填的，只认 http(s)，防止 javascript: 这类链接 */
function contactLine(): string {
  const c = page.value?.contact
  if (!c) return ''
  return [c.realName && `姓名 ${c.realName}`, c.wechat && `微信 ${c.wechat}`, c.qq && `QQ ${c.qq}`].filter(Boolean).join(' · ')
}
</script>

<template>
  <el-drawer v-model="store.visible" :with-header="false" :size="narrow ? '100%' : '500px'" class="user-page-drawer" append-to-body>
    <div v-loading="loading" class="page">
      <button type="button" class="close" aria-label="关闭" @click="store.close()">
        <el-icon :size="16"><Close /></el-icon>
      </button>

      <p v-if="error && !loading" class="error">{{ error }}</p>

      <template v-if="page && !error">
        <!-- 头部：方头像、名字、认证和信用，下面一条粗线 -->
        <header class="hero">
          <div class="kicker">PROFILE <span class="zh">/ 个人资料</span></div>
          <div class="hero-row">
            <div class="avatar" aria-hidden="true">{{ page.nickname.slice(0, 1) }}</div>
            <div class="hero-text">
              <h2 class="display name">{{ page.nickname }}</h2>
              <div v-if="subtitle" class="subtitle">{{ subtitle }}</div>
              <div class="badges">
                <el-tag v-if="page.verified" size="small" type="success" effect="plain">已认证</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">未认证</el-tag>
                <el-tag
                  v-if="page.credit.score !== null"
                  size="small"
                  effect="plain"
                  :type="creditType(page.credit.score)"
                  :title="`赛后互评得出的信用分，来自 ${page.credit.ratedRounds} 场比赛`"
                >
                  信用 {{ Math.round(page.credit.score) }}
                </el-tag>
                <el-tag v-else size="small" type="info" effect="plain" title="还没有互评记录">新用户</el-tag>
                <span class="since num">
                  <template v-if="page.stats">加入 {{ page.stats.memberDays }} 天</template>
                  <template v-else-if="page.joinedAt">{{ fmtDate(page.joinedAt, true) }} 加入</template>
                </span>
              </div>
            </div>
          </div>
          <hr class="rule" />

          <div v-if="page.highlight" class="honor">
            <div class="kicker">HONOR <span class="zh">/ 最高荣誉</span></div>
            <div class="honor-row">
              <div class="honor-text">
                <strong>{{ page.highlight.award }}</strong>
                <span>{{ page.highlight.title }} · {{ page.highlight.year }}</span>
              </div>
              <el-tag size="small" effect="plain" :type="sourceOf(page.highlight).type">{{ sourceOf(page.highlight).label }}</el-tag>
            </div>
          </div>
        </header>

        <!-- 看自己：可以切到"别人看到的样子" -->
        <div v-if="isOwner" class="self-bar">
          <span>这是你的资料页</span>
          <el-switch v-model="store.preview" size="small" active-text="以其他同学的身份预览" />
          <router-link to="/profile" class="self-edit">编辑档案 →</router-link>
        </div>

        <p v-if="page.access.notice" class="notice">{{ page.access.notice }}</p>

        <section v-if="hasMutual" class="block">
          <div class="kicker">IN COMMON <span class="zh">/ 你们的共同点</span></div>
          <div class="chips">
            <span v-if="page.mutual!.sameSchool" class="chip">同校</span>
            <span v-for="t in page.mutual!.teams" :key="`t${t.teamId}`" class="chip strong">
              {{ t.current ? '现在是队友' : '曾经同队' }} · {{ t.teamName }}
            </span>
            <span v-for="e in page.mutual!.editions" :key="`e${e.competitionId}${e.editionTitle}`" class="chip">
              都关注了 {{ e.competitionName }}
            </span>
          </div>
        </section>

        <section v-if="hasAbout" class="block">
          <div class="kicker">ABOUT <span class="zh">/ {{ page.access.level === 'SELF' ? '关于我' : '关于 TA' }}</span></div>
          <p v-if="page.bio" class="bio">{{ page.bio }}</p>
          <div v-if="page.selfIntro" class="quote">
            <span class="quote-label">能给队伍带来的</span>
            {{ page.selfIntro }}
          </div>
          <div v-if="page.weakness" class="quote">
            <span class="quote-label">希望队友包容的短板</span>
            {{ page.weakness }}
          </div>
        </section>

        <!-- 生涯数据：像游戏里的战绩概览 -->
        <section v-if="page.stats" class="block">
          <div class="kicker">CAREER <span class="zh">/ 生涯数据</span></div>
          <div class="stats">
            <div class="stat">
              <div class="stat-value num">{{ page.stats.teams }}</div>
              <div class="stat-label">平台组队</div>
            </div>
            <div class="stat">
              <div class="stat-value num">{{ page.stats.finished }}</div>
              <div class="stat-label">完赛</div>
            </div>
            <div class="stat">
              <div class="stat-value num">{{ page.stats.captainTimes }}</div>
              <div class="stat-label">当过队长</div>
            </div>
            <div class="stat">
              <div class="stat-value num">{{ page.stats.awards }}</div>
              <div class="stat-label">获奖</div>
            </div>
          </div>
        </section>

        <!-- 技能：先看六维能力形状，再看具体技能（三格表示入门、熟练、精通） -->
        <section class="block">
          <div class="kicker">SKILLS <span class="zh">/ 技能</span></div>
          <SkillRadar
            v-if="page.radar.count > 0"
            :axes="page.radar.axes"
            :series="[{ name: '能力', values: page.radar.axes.map((a) => a.score), color: '#121310' }]"
            :height="210"
            compact
          />
          <p v-if="page.radar.count > 0" class="radar-advice">{{ page.radar.advice }}</p>
          <p v-if="page.skills.length === 0" class="empty">还没有填写技能。</p>
          <!-- 技能按六类能力归类，雷达图的每一根轴对应下面一块 -->
          <RadarSkillGroups v-else :axes="page.radar.axes" mode="person" only-filled />
        </section>

        <!-- 看不到履历时，像游戏平台的"此资料是私密的"一样给一块提示 -->
        <section v-if="!page.access.historyVisible" class="block">
          <div class="locked">
            <el-icon :size="20"><Lock /></el-icon>
            <span>参赛履历和组队记录暂时看不到</span>
          </div>
        </section>

        <template v-else>
          <!-- 组队记录：像游戏库，每支队一行，后面是在队天数 -->
          <section class="block">
            <div class="kicker">TEAMS <span class="zh">/ 平台组队记录 · {{ page.teams.length }}</span></div>
            <p v-if="page.teams.length === 0" class="empty">还没有在平台上组过队。</p>
            <div v-for="t in page.teams" :key="t.teamId" class="record" :class="t.state.toLowerCase()">
              <div class="record-main">
                <div class="record-title">{{ t.competitionName }} · {{ t.editionTitle }}</div>
                <div class="record-sub">
                  <router-link :to="{ name: 'team', params: { id: t.teamId } }">{{ t.teamName }}</router-link>
                  <el-tag v-if="t.captain" size="small" type="warning" effect="plain">队长</el-tag>
                  <el-tag v-if="t.roleName" size="small" type="info" effect="plain">{{ t.roleName }}</el-tag>
                  <el-tag v-if="t.award" size="small" type="warning">{{ t.award }}</el-tag>
                </div>
              </div>
              <div class="record-side">
                <el-tag size="small" effect="plain" :type="TEAM_RECORD_STATE[t.state].type">{{ TEAM_RECORD_STATE[t.state].label }}</el-tag>
                <div v-if="t.days !== null" class="record-days">
                  {{ t.state === 'IN_TEAM' ? '已在队' : '在队' }} <b class="num">{{ t.days }}</b> 天
                </div>
              </div>
            </div>
          </section>

          <!-- 参赛履历：每条标明来源 -->
          <section class="block">
            <div class="kicker">RECORD <span class="zh">/ 参赛履历 · {{ page.experiences.length }}</span></div>
            <p v-if="page.experiences.length === 0" class="empty">还没有参赛履历。</p>
            <div v-for="e in page.experiences" :key="e.id" class="experience">
              <span class="exp-year num">{{ e.year }}</span>
              <div class="exp-main">
                <div class="exp-title">
                  {{ e.title }}
                  <a v-if="safeUrl(e.proofUrl)" :href="safeUrl(e.proofUrl)!" target="_blank" rel="noopener noreferrer" class="exp-proof">证明 ↗</a>
                  <span v-else-if="e.hasProof" class="exp-proof-hidden" title="证明材料上一般有真实姓名，只给队友和主动联系过的人看">有证明材料</span>
                </div>
                <div class="exp-tags">
                  <el-tag v-if="e.award" size="small" type="warning">{{ e.award }}</el-tag>
                  <el-tag size="small" effect="plain" :type="sourceOf(e).type">{{ sourceOf(e).label }}</el-tag>
                </div>
              </div>
            </div>
            <p v-if="page.experiences.some((e) => e.source === 'MANUAL' && !e.verified)" class="hint">
              "自己填写"的履历平台没有核验过，组队前可以请对方出示证书。
            </p>
          </section>
        </template>

        <section class="block last">
          <div class="kicker">CONTACT <span class="zh">/ 联系方式</span></div>
          <p v-if="page.access.contactVisible" class="contact">{{ contactLine() || '还没填联系方式' }}</p>
          <p v-else class="empty">成为队友后可以看到真实姓名、微信和 QQ。</p>
        </section>
      </template>
    </div>
  </el-drawer>
</template>

<style>
/* 抽屉会被挪到 body 下面，外壳的样式只能写成全局的；用专门的类名限定范围 */
.user-page-drawer .el-drawer__body {
  padding: 0;
  background: var(--jd-paper);
}
</style>

<style scoped>
.page {
  position: relative;
  min-height: 100%;
  padding: 28px 28px 32px;
}

.close {
  position: absolute;
  top: 18px;
  right: 18px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--jd-line-strong);
  background: transparent;
  color: var(--jd-ink);
  cursor: pointer;
}

.close:hover {
  border-color: var(--jd-ink);
  background: var(--el-fill-color-light);
}

.error {
  margin: 60px 0 0;
  color: var(--jd-red);
}

.hero-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 12px;
}

.avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  background: var(--jd-ink);
  color: var(--jd-paper);
  font-size: 28px;
  font-weight: 700;
}

.hero-text {
  min-width: 0;
}

.name {
  font-size: 26px;
}

.subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--jd-ink-2);
}

.badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.since {
  font-size: 12px;
  color: var(--jd-muted);
}

.hero .rule {
  margin: 16px 0 0;
}

.honor {
  margin-top: 14px;
  padding: 10px 14px;
  background: var(--jd-accent-soft);
  border-left: 3px solid var(--jd-accent);
}

.honor-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 4px;
}

.honor-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.honor-text strong {
  font-size: 16px;
}

.honor-text span {
  font-size: 12px;
  color: var(--jd-ink-2);
}

.self-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid var(--jd-line-light);
  background: var(--jd-panel);
  font-size: 13px;
}

.self-edit {
  margin-left: auto;
  font-size: 12px;
  text-decoration: none;
}

.notice {
  margin: 16px 0 0;
  padding: 10px 14px;
  border-left: 3px solid var(--jd-line-strong);
  background: var(--jd-panel);
  font-size: 13px;
  line-height: 1.6;
  color: var(--jd-ink-2);
}

.block {
  padding: 18px 0;
  border-bottom: 1px solid var(--jd-line-light);
}

.block.last {
  border-bottom: none;
  padding-bottom: 0;
}

.block > .kicker {
  margin-bottom: 10px;
}

.empty,
.hint {
  margin: 0;
  font-size: 13px;
  color: var(--jd-muted);
}

.hint {
  margin-top: 10px;
  font-size: 12px;
}

.radar-advice {
  margin: 2px 0 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--jd-muted);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 3px 10px;
  border: 1px solid var(--jd-line);
  font-size: 13px;
  color: var(--jd-ink-2);
}

.chip.strong {
  border-color: var(--jd-ink);
  color: var(--jd-ink);
  font-weight: 500;
}

.bio {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.quote {
  margin-top: 8px;
  padding: 8px 12px;
  border-left: 2px solid var(--jd-ink);
  background: var(--jd-panel);
  font-size: 14px;
  line-height: 1.6;
}

.quote-label {
  display: block;
  font-size: 11px;
  color: var(--jd-muted);
  letter-spacing: 0.06em;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border: 1px solid var(--jd-line-light);
}

.stat {
  padding: 12px 0 10px;
  text-align: center;
  background: var(--jd-panel);
}

.stat + .stat {
  border-left: 1px solid var(--jd-line-light);
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

.stat-label {
  margin-top: 4px;
  font-size: 11px;
  color: var(--jd-muted);
  letter-spacing: 0.06em;
}

.locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 22px 0;
  border: 1px dashed var(--jd-line-strong);
  font-size: 13px;
  color: var(--jd-muted);
}

.record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--jd-line-light);
  border-left: 3px solid var(--jd-ink);
  background: var(--jd-panel);
}

.record.finished {
  border-left-color: var(--jd-olive);
}

.record.left,
.record.disbanded {
  border-left-color: var(--jd-line-strong);
}

.record-main {
  min-width: 0;
}

.record-title {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 13px;
}

.record-side {
  flex: none;
  text-align: right;
}

.record-days {
  margin-top: 4px;
  font-size: 12px;
  color: var(--jd-muted);
}

.record-days b {
  font-size: 15px;
  color: var(--jd-ink);
}

.experience {
  display: flex;
  gap: 12px;
  padding: 8px 0;
}

.experience + .experience {
  border-top: 1px dashed var(--jd-line-lighter);
}

.exp-year {
  flex: none;
  width: 40px;
  padding-top: 1px;
  font-size: 13px;
  color: var(--jd-muted);
}

.exp-main {
  min-width: 0;
}

.exp-title {
  font-size: 14px;
}

.exp-proof-hidden {
  margin-left: 6px;
  font-size: 12px;
  color: var(--jd-muted);
}

.exp-proof {
  margin-left: 6px;
  font-size: 12px;
}

.exp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.contact {
  margin: 0;
  font-size: 14px;
}
</style>

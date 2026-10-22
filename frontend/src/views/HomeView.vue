<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import HomeMiddle from '@/components/home/HomeMiddle.vue'
import HomeSide from '@/components/home/HomeSide.vue'
import HomeTopStrip from '@/components/home/HomeTopStrip.vue'
import ScheduleCalendar from '@/components/home/ScheduleCalendar.vue'
import { getHome } from '@/api/home'
import { listNotifications } from '@/api/notification'
import { homeRecommendations, surveyProgress } from '@/api/survey'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import type { HomeData, HomeRecommendation, Notification } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 我的主页（项目思路里的 B 版三栏，加上 C 版的一句话状态和每周冲突预警）：
 * 左边是赛程日历（默认占 1/3，可以加宽），中间是比赛，右边是组队要你决定的事。
 */
const user = useUserStore()
const notifications = useNotificationStore()

const data = ref<HomeData | null>(null)
const recs = ref<HomeRecommendation[]>([])
const messages = ref<Notification[]>([])
const generalDone = ref(true)
const generalQuick = ref(false)
const loading = ref(true)

/** 日历栏默认占 1/3；点日历右上角的「加宽」换回改版前的比例，记在本机 */
const WIDE_KEY = 'tm_home_wide_calendar'

function loadWide(): boolean {
  try {
    return localStorage.getItem(WIDE_KEY) === '1'
  } catch {
    return false
  }
}

const wideCalendar = ref(loadWide())

function toggleWide() {
  wideCalendar.value = !wideCalendar.value
  try {
    localStorage.setItem(WIDE_KEY, wideCalendar.value ? '1' : '0')
  } catch {
    // 存不了就只在这次有效
  }
}

async function load() {
  try {
    const [home, r, p, n] = await Promise.all([getHome(), homeRecommendations(), surveyProgress(), listNotifications(1)])
    data.value = home
    recs.value = r
    generalDone.value = p.generalDone
    generalQuick.value = p.generalQuick
    messages.value = n.records.slice(0, 4)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
// 推送来了新消息（新的邀请、申请、结果），主页跟着刷新
watch(() => notifications.latest, load)
</script>

<template>
  <div v-loading="loading" class="home">
    <div v-if="user.me?.verifyStatus === 'NONE'" class="notice">
      完成学校认证后，就能建队和申请入队。<router-link to="/verify">去认证 →</router-link>
    </div>
    <div v-if="!loading && !generalDone" class="notice">
      先花 1 分钟填一下组队问卷，推荐队友时才知道你们合不合得来；勾上空闲时段，还能估算每周能投入多少时间。
      <router-link to="/survey">去填问卷 →</router-link>
    </div>
    <div v-else-if="!loading && generalQuick" class="notice">
      你填的是快速版，补全完整版后推荐会更准。<router-link to="/survey">补全问卷 →</router-link>
    </div>

    <template v-if="data">
      <HomeTopStrip :data="data" />
      <div class="grid" :class="{ 'wide-calendar': wideCalendar }">
        <ScheduleCalendar
          :semester="data.semester"
          :today="data.today"
          :wide="wideCalendar"
          class="left"
          @changed="load"
          @toggle-wide="toggleWide"
        />
        <HomeMiddle :mine="data.mine" :suggestions="data.suggestions" />
        <HomeSide :pending="data.pending" :teammates="data.teammates" :recommendations="recs" :messages="messages" @changed="load" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.home {
  max-width: 1440px;
  min-height: 360px;
  margin: 0 auto;
}

.notice {
  margin-bottom: 10px;
  padding: 8px 12px;
  border-left: 2px solid var(--jd-accent);
  background: var(--jd-accent-soft);
  font-size: 13px;
}

.notice a {
  margin-left: 6px;
  font-weight: 700;
}

/* 三栏的宽度比例：日历、比赛、组队。要调比例只改这几个变量 */
.grid {
  --col-calendar: 1fr;
  --col-middle: 1fr;
  --col-side: 1fr;
  display: grid;
  grid-template-columns: minmax(320px, var(--col-calendar)) minmax(300px, var(--col-middle)) minmax(260px, var(--col-side));
  gap: 16px;
  align-items: start;
}

/* 加宽日历：改版前的比例 */
.grid.wide-calendar {
  --col-calendar: 1.3fr;
  --col-middle: 1fr;
  --col-side: 0.85fr;
}

@media (max-width: 1200px) {
  .grid,
  .grid.wide-calendar {
    grid-template-columns: 1fr 1fr;
  }

  .left {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .grid,
  .grid.wide-calendar {
    grid-template-columns: 1fr;
  }
}
</style>

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
 * 左边以赛程日历为主体，中间是比赛，右边是组队要你决定的事。
 */
const user = useUserStore()
const notifications = useNotificationStore()

const data = ref<HomeData | null>(null)
const recs = ref<HomeRecommendation[]>([])
const messages = ref<Notification[]>([])
const generalDone = ref(true)
const generalQuick = ref(false)
const loading = ref(true)

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
      <div class="grid">
        <ScheduleCalendar :semester="data.semester" :today="data.today" class="left" />
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

.grid {
  display: grid;
  grid-template-columns: minmax(360px, 1.3fr) minmax(300px, 1fr) minmax(260px, 0.85fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1200px) {
  .grid {
    grid-template-columns: 1fr 1fr;
  }

  .left {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

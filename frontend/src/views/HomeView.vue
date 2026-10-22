<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import HomeLayoutDrawer from '@/components/home/HomeLayoutDrawer.vue'
import HomeMiddle from '@/components/home/HomeMiddle.vue'
import HomeSide from '@/components/home/HomeSide.vue'
import HomeTopStrip from '@/components/home/HomeTopStrip.vue'
import ScheduleCalendar from '@/components/home/ScheduleCalendar.vue'
import SemesterBand from '@/components/home/SemesterBand.vue'
import { HOME_LAYOUT, HOME_PANELS, defaultLayout, type HomeColumn } from '@/components/home/layout'
import { getHome, getHomeLayout, saveHomeLayout } from '@/api/home'
import { listNotifications } from '@/api/notification'
import { homeRecommendations, surveyProgress } from '@/api/survey'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import type { HomeData, HomeLayout, HomeRecommendation, Notification } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 我的主页（项目思路里的 B 版三栏，加上 C 版的一句话状态和每周冲突预警）：
 * 左边是赛程日历和学期赛程带（默认占 1/3，可以加宽），中间是比赛，右边是组队要你决定的事。
 * 右上角「自定义主页」可以隐藏面板、在同一栏里调顺序，设置存在账号里。
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

// ---------- 主页自定义 ----------

const layout = ref<HomeLayout>(defaultLayout())
const customizing = ref(false)
let saveTimer: ReturnType<typeof setTimeout> | undefined

async function loadLayout() {
  try {
    layout.value = await getHomeLayout()
  } catch {
    // 拿不到就用默认排法，不影响看主页
  }
}

/** 改了马上生效，停手 0.4 秒后存到账号里 */
function changeLayout(next: HomeLayout) {
  layout.value = next
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => saveHomeLayout(next).catch(showError), 400)
}

function toggleWide() {
  changeLayout({ ...layout.value, wideCalendar: !layout.value.wideCalendar })
}

const shown = (key: string) => !layout.value.hidden.includes(key)
/** 给 CSS order 用：同一栏里按 order 里的先后排 */
const pos = (key: string) => layout.value.order.indexOf(key)
provide(HOME_LAYOUT, { pos, shown })

const colVisible = (column: HomeColumn) => HOME_PANELS.some((p) => p.column === column && shown(p.key))

/** 三栏的宽度：比例在下面 CSS 的 --col-* 变量里；整栏都隐藏了就不占位置 */
const TRACKS: Record<'left' | 'middle' | 'right', string> = {
  left: 'minmax(320px, var(--col-calendar))',
  middle: 'minmax(300px, var(--col-middle))',
  right: 'minmax(260px, var(--col-side))',
}
const gridCols = computed(() =>
  (['left', 'middle', 'right'] as const)
    .filter(colVisible)
    .map((c) => TRACKS[c])
    .join(' '),
)

onMounted(() => {
  load()
  loadLayout()
})
onBeforeUnmount(() => clearTimeout(saveTimer))
// 推送来了新消息（新的邀请、申请、结果），主页跟着刷新
watch(() => notifications.latest, load)
</script>

<template>
  <div v-loading="loading" class="home">
    <div class="toolbar">
      <button type="button" class="customize" @click="customizing = true">
        <el-icon><Setting /></el-icon>自定义主页
      </button>
    </div>
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
      <HomeTopStrip v-show="shown('today')" :data="data" />
      <div class="grid" :class="{ 'wide-calendar': layout.wideCalendar }" :style="{ '--grid-cols': gridCols }">
        <div v-if="colVisible('left')" class="col left">
          <ScheduleCalendar
            v-show="shown('schedule')"
            :style="{ order: pos('schedule') }"
            :today="data.today"
            :wide="layout.wideCalendar"
            @changed="load"
            @toggle-wide="toggleWide"
          />
          <section v-show="shown('semester')" class="panel" :style="{ order: pos('semester') }">
            <SemesterBand :semester="data.semester" :today="data.today" />
          </section>
        </div>
        <HomeMiddle v-if="colVisible('middle')" :mine="data.mine" :suggestions="data.suggestions" />
        <HomeSide :pending="data.pending" :teammates="data.teammates" :recommendations="recs" :messages="messages" @changed="load" />
      </div>
    </template>

    <HomeLayoutDrawer v-model="customizing" :layout="layout" @change="changeLayout" />
  </div>
</template>

<style scoped>
.home {
  max-width: 1440px;
  min-height: 360px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin: -4px 0 6px;
}

.customize {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  color: var(--jd-muted);
  cursor: pointer;
}

.customize:hover {
  color: var(--jd-accent);
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

/* 三栏的宽度比例：日历、比赛、组队。要调比例只改这几个变量（哪几栏在由上面的 gridCols 决定） */
.grid {
  --col-calendar: 1fr;
  --col-middle: 1fr;
  --col-side: 1fr;
  display: grid;
  grid-template-columns: var(--grid-cols);
  gap: 16px;
  align-items: start;
}

/* 加宽日历：改版前的比例 */
.grid.wide-calendar {
  --col-calendar: 1.3fr;
  --col-middle: 1fr;
  --col-side: 0.85fr;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
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

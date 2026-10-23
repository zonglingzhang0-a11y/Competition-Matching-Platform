<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardOps } from '@/types/api'
import { fmtDateTime } from '@/utils/time'

/**
 * 看板"运营"：四小块，每块写清楚现在的情况和该做什么。
 * 申请和邀请（谁手上压着）、认证审核（按学校，没收录的后缀单独标出）、爬虫来源（哪个出错、好几天没抓）、举报和信用。
 */
const props = defineProps<{ ops: DashboardOps }>()

const SOURCE_STATUS: Record<DashboardOps['sources'][number]['status'], { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  OK: { label: '正常', type: 'success' },
  ERROR: { label: '上次出错', type: 'danger' },
  STALE: { label: '好几天没抓', type: 'warning' },
  NEVER: { label: '还没抓过', type: 'info' },
  DISABLED: { label: '停用', type: 'info' },
}

const toConfirm = computed(() => props.ops.sources.reduce((s, x) => s + x.toConfirm, 0))
const brokenSources = computed(() => props.ops.sources.filter((s) => s.status === 'ERROR' || s.status === 'STALE').length)
const unknownDomains = computed(() => props.ops.verifications.groups.filter((g) => g.unknownDomain))

const hours = (h: number | null) => (h === null ? '—' : h < 1 ? `${Math.round(h * 60)} 分钟` : `${h} 小时`)
</script>

<template>
  <div class="ops">
    <!-- 申请和邀请 -->
    <section class="box" :class="{ alert: ops.applications.overdue > 0 }">
      <h4>申请和邀请</h4>
      <p class="big">
        <b class="num">{{ ops.applications.pending }}</b> 条等处理<template v-if="ops.applications.overdue">，
          <b class="num red">{{ ops.applications.overdue }}</b> 条等了 3 天以上</template>
      </p>
      <p class="sub">
        最近 30 天处理完 {{ ops.applications.handled30d }} 条，一般等 {{ hours(ops.applications.medianHours) }}；作废
        {{ ops.applications.expired30d }} 条（对方进了别的队，或者队伍满员、解散）
      </p>
      <ul v-if="ops.applications.backlog.length" class="list">
        <li v-for="b in ops.applications.backlog" :key="b.userId + b.direction">
          <span>{{ b.nickname }}</span>
          <span class="muted">{{ b.direction === 'APPLY' ? '队长，压着申请' : '被邀请，还没回' }} {{ b.count }} 条，最久 {{ b.oldestDays }} 天</span>
        </li>
      </ul>
      <p class="todo">{{ ops.applications.backlog.length ? '可以私下提醒这几位去处理，申请一直没人回会让人放弃。' : '没有积压。' }}</p>
    </section>

    <!-- 认证审核 -->
    <section class="box" :class="{ alert: ops.verifications.pending > 0 }">
      <h4>认证审核</h4>
      <p class="big">
        <b class="num">{{ ops.verifications.pending }}</b> 条待审核<template v-if="ops.verifications.oldestDays !== null">，最久等了
          <b class="num" :class="{ red: ops.verifications.oldestDays >= 2 }">{{ ops.verifications.oldestDays }}</b> 天</template>
      </p>
      <ul v-if="ops.verifications.groups.length" class="list">
        <li v-for="g in ops.verifications.groups" :key="g.label">
          <span>{{ g.label }}</span>
          <span class="muted">{{ g.count }} 条，最久 {{ g.oldestDays }} 天</span>
          <el-tag v-if="g.unknownDomain" size="small" type="warning" effect="plain">后缀没收录</el-tag>
        </li>
      </ul>
      <p class="todo">
        <template v-if="unknownDomains.length">通过时顺手收录邮箱后缀，同校的人以后自动通过。</template>
        <template v-else-if="!ops.verifications.pending">没有待审核的。</template>
        <router-link v-if="ops.verifications.pending" to="/admin/verifications">去审核 →</router-link>
      </p>
    </section>

    <!-- 爬虫来源 -->
    <section class="box" :class="{ alert: brokenSources > 0 }">
      <h4>爬虫来源</h4>
      <p class="big">
        <b class="num">{{ ops.sources.length }}</b> 个来源<template v-if="brokenSources">，
          <b class="num red">{{ brokenSources }}</b> 个要看看</template><template v-if="toConfirm">，<b class="num">{{ toConfirm }}</b> 篇通知等确认</template>
      </p>
      <ul class="list">
        <li v-for="s in ops.sources" :key="s.id" :title="s.lastResult ?? ''">
          <span>{{ s.name }}</span>
          <el-tag size="small" :type="SOURCE_STATUS[s.status].type" effect="plain">{{ SOURCE_STATUS[s.status].label }}</el-tag>
          <span class="muted">
            {{ s.lastRunAt ? fmtDateTime(s.lastRunAt) : '' }}<template v-if="s.toConfirm"> · {{ s.toConfirm }} 篇待确认</template>
          </span>
        </li>
      </ul>
      <p class="todo">
        <template v-if="brokenSources">出错的多半是官网改版，点"试抓"看看选择器还对不对。</template>
        <router-link to="/admin/crawl">去通知抓取 →</router-link>
      </p>
    </section>

    <!-- 举报和信用 -->
    <section class="box" :class="{ alert: ops.reports.pending > 0 }">
      <h4>举报和信用</h4>
      <p class="big">
        <b class="num">{{ ops.reports.pending }}</b> 条举报待处理<template v-if="ops.reports.oldestDays !== null">，最久
          <b class="num" :class="{ red: ops.reports.oldestDays >= 3 }">{{ ops.reports.oldestDays }}</b> 天</template>
      </p>
      <p class="sub">最近 90 天处理一条一般要 {{ hours(ops.reports.medianHours) }}；现在暂停推荐的 {{ ops.reports.suspendedUsers }} 人</p>
      <p class="todo">
        <template v-if="!ops.reports.pending">没有待处理的举报。</template>
        <router-link v-else to="/admin/reports">去处理 →</router-link>
      </p>
    </section>
  </div>
</template>

<style scoped>
.ops {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.box {
  padding: 12px 14px;
  border: 1px solid var(--jd-line-light);
  border-left: 3px solid var(--jd-line-light);
  background: var(--jd-panel);
}

.box.alert {
  border-left-color: var(--jd-red);
}

h4 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 700;
}

.big {
  margin: 0;
  font-size: 13px;
}

.big .num {
  font-size: 20px;
}

.red {
  color: var(--jd-red);
}

.sub,
.todo {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--jd-muted);
}

.todo a {
  margin-left: 4px;
  font-weight: 700;
}

.list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  border-top: 1px solid var(--jd-line-lighter);
  font-size: 12px;
}

.muted {
  color: var(--jd-muted);
}

@media (max-width: 760px) {
  .ops {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

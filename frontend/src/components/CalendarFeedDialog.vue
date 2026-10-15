<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCalendarFeed, resetCalendarFeed } from '@/api/calendar'
import { showError } from '@/utils/feedback'

/**
 * 订阅到手机日历：给出私有订阅链接（iCalendar），手机、电脑自带的日历订阅后自动同步赛程，截止提前一天提醒。
 */
const open = defineModel<boolean>({ required: true })

const path = ref('')
const loading = ref(false)

const url = computed(() => (path.value ? `${location.origin}${path.value}` : ''))
const webcal = computed(() => url.value.replace(/^https?:/, 'webcal:'))
/** 本机运行时手机访问不到 localhost */
const local = computed(() => /^(localhost|127\.|\[::1\])/.test(location.hostname))

watch(open, async (value) => {
  if (!value || path.value) return
  loading.value = true
  try {
    path.value = (await getCalendarFeed()).path
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
})

async function copy() {
  try {
    await navigator.clipboard.writeText(url.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制不了，请手动选中链接复制')
  }
}

async function reset() {
  try {
    await ElMessageBox.confirm('重置后旧链接马上失效，已经订阅的日历要用新链接重新订阅。', '重置订阅链接', {
      type: 'warning',
      confirmButtonText: '重置',
    })
  } catch {
    return
  }
  try {
    path.value = (await resetCalendarFeed()).path
    ElMessage.success('已换成新链接')
  } catch (e) {
    showError(e)
  }
}
</script>

<template>
  <el-dialog v-model="open" title="订阅到手机日历" width="560px">
    <div v-loading="loading">
      <p class="kicker">CALENDAR FEED <span class="zh">/ 日历订阅</span></p>
      <p class="lead">
        把比赛节点、队伍节点和个人日程同步到手机、电脑自带的日历里，截止日期提前一天提醒。日历应用每隔几小时自动来取一次新的赛程。
      </p>

      <div class="url">
        <el-input :model-value="url" readonly aria-label="订阅链接" />
        <el-button @click="copy">复制</el-button>
      </div>
      <div class="actions">
        <a :href="webcal" class="action strong">用日历应用打开 ↗</a>
        <a :href="url" download="jingda.ics" class="action">下载 .ics 文件 ↓</a>
      </div>

      <ol class="steps">
        <li><strong>iPhone</strong>：设置 → 日历 → 账户 → 添加账户 → 其他 → 添加已订阅的日历，粘贴链接。</li>
        <li><strong>安卓 / Google 日历</strong>：电脑上打开 Google 日历网页版 → 其他日历 → 通过网址添加。</li>
        <li><strong>电脑 Outlook</strong>：添加日历 → 从 Internet 订阅，粘贴链接。</li>
      </ol>

      <p v-if="local" class="note">
        现在网站跑在你自己的电脑上，手机访问不到这个链接；部署到服务器后手机才能订阅。可以先下载 .ics 文件导入电脑的日历试试。
      </p>
      <p class="note">这个链接就是你赛程的钥匙，不要发给别人。万一泄露了，点下面的"重置链接"，旧链接马上失效。</p>
    </div>
    <template #footer>
      <el-button text type="danger" class="left" @click="reset">重置链接</el-button>
      <el-button type="primary" @click="open = false">好</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.lead {
  margin: 6px 0 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--jd-ink-2);
}

.url {
  display: flex;
  gap: 8px;
}

.actions {
  display: flex;
  gap: 20px;
  margin: 10px 0 14px;
}

.action {
  font-size: 13px;
  text-decoration: none;
}

.action.strong {
  font-weight: 700;
}

.steps {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--jd-ink-2);
}

.note {
  margin: 10px 0 0;
  padding: 6px 10px;
  border-left: 2px solid var(--jd-line-strong);
  font-size: 12px;
  line-height: 1.6;
  color: var(--jd-muted);
}

.left {
  float: left;
}
</style>

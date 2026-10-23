<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Link } from '@element-plus/icons-vue'
import { generateAiSummary, getAiSummary } from '@/api/competition'
import type { AiSummaryView } from '@/types/api'
import { fmtDateTime } from '@/utils/time'
import { safeUrl } from '@/utils/url'
import { showError } from '@/utils/feedback'

/**
 * 比赛页的「AI 帮我整理」：从右边滑出来，显示平台抓到的官方通知整理成的要点、没整理出来的关键项、更多信息渠道。
 * <p>
 * 本机算力有限，学生打开时不调大模型，而是把管理员整理好存下来的结果一步步放出来（每一步说的都是这份总结实际做过的事，
 * 顶上写明整理时间）；管理员点「重新整理」才真的调大模型。
 */
const props = defineProps<{ competitionId: number; competitionName: string }>()
const open = defineModel<boolean>({ required: true })
/** 点了"去看看"：跳到比赛页上的加分认定或往届队伍 */
const emit = defineEmits<{ jump: [anchor: 'recognition' | 'editions'] }>()

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制「${text}」，去微信里搜吧`)
  } catch {
    ElMessage.info(`复制不了，手动记一下：${text}`)
  }
}

/** 手机上铺满整屏 */
const narrowQuery = window.matchMedia('(max-width: 560px)')
const narrow = ref(narrowQuery.matches)
const onNarrow = (e: MediaQueryListEvent) => (narrow.value = e.matches)
narrowQuery.addEventListener('change', onNarrow)

const view = ref<AiSummaryView | null>(null)
const loading = ref(false)
const generating = ref(false)
/** 放到第几步、第几条要点；放完以后两个都是总数 */
const stepsShown = ref(0)
const pointsShown = ref(0)
let timer: ReturnType<typeof setTimeout> | undefined

const summary = computed(() => view.value?.summary ?? null)

/** 这份总结经过的几步，放的时候一条条出来 */
const steps = computed(() => {
  const v = view.value
  const s = v?.summary
  if (!v || !s) return []
  const used = v.sources.filter((x) => x.used).length
  const blocked = v.sources.filter((x) => !x.aiAllowed).length
  return [
    `读了平台抓到的 ${used} 篇官方通知` + (blocked ? `（另有 ${blocked} 篇来源网站不让 AI 读，只放链接）` : ''),
    `整理出 ${s.points.length} 条要点`,
    '每条都对照通知原文核对过，对不上原文的已经去掉',
    `列出 ${v.channels.length} 个可以继续查的地方`,
  ]
})

const playing = computed(
  () => !!summary.value && (stepsShown.value < steps.value.length || pointsShown.value < summary.value.points.length),
)

function stop() {
  if (timer) clearTimeout(timer)
  timer = undefined
}

function showAll() {
  stop()
  stepsShown.value = steps.value.length
  pointsShown.value = summary.value?.points.length ?? 0
}

/** 一步步放出来；系统设置了减少动画的直接全部显示 */
function play() {
  stop()
  stepsShown.value = 0
  pointsShown.value = 0
  if (!summary.value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    showAll()
    return
  }
  const tick = () => {
    if (stepsShown.value < steps.value.length) {
      stepsShown.value += 1
      timer = setTimeout(tick, 450)
    } else if (pointsShown.value < (summary.value?.points.length ?? 0)) {
      pointsShown.value += 1
      timer = setTimeout(tick, 280)
    } else {
      timer = undefined
    }
  }
  timer = setTimeout(tick, 300)
}

async function load() {
  loading.value = true
  try {
    view.value = await getAiSummary(props.competitionId)
    play()
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

/** 管理员：真的调大模型；失败时原来的总结不动 */
async function generate() {
  stop()
  generating.value = true
  try {
    view.value = await generateAiSummary(props.competitionId)
    play()
  } catch (e) {
    showError(e)
    showAll()
  } finally {
    generating.value = false
  }
}

watch(open, (v) => (v ? load() : stop()))
onBeforeUnmount(() => {
  stop()
  narrowQuery.removeEventListener('change', onNarrow)
})

function sourceLabel(title: string | null): string {
  return title?.trim() || '官方通知'
}
</script>

<template>
  <el-drawer v-model="open" :with-header="false" :size="narrow ? '100%' : '520px'" class="ai-summary-drawer" append-to-body>
    <div v-loading="loading" class="page">
      <button type="button" class="close" aria-label="关闭" @click="open = false">
        <el-icon :size="16"><Close /></el-icon>
      </button>

      <div class="kicker">AI SUMMARY <span class="zh">/ AI 整理</span></div>
      <h2 class="name">{{ competitionName }}</h2>

      <p class="warn">
        这是 AI 根据平台抓到的官方通知整理的，<b>可能不全，也可能已经过时</b>。报名时间、截止日期这类要紧的事，请以官网和学校的通知为准。
      </p>

      <template v-if="view">
        <div class="meta">
          <span v-if="summary">整理于 {{ fmtDateTime(summary.generatedAt) }} · 模型 {{ summary.model }}</span>
          <span v-else>还没有整理过这个比赛</span>
          <el-button
            v-if="view.canGenerate"
            size="small"
            :loading="generating"
            :disabled="!view.llmReady"
            @click="generate"
          >
            {{ summary ? '重新整理' : '整理' }}
          </el-button>
        </div>
        <p v-if="view.canGenerate && !view.llmReady" class="hint">
          后端还没打开大模型（配置里的 app.crawler.llm.enabled），现在只能看已经存好的结果。
        </p>
        <p v-if="generating" class="hint">正在请大模型整理，本机模型要等几秒到几十秒……</p>

        <!-- 整理经过的几步 -->
        <ol v-if="summary" class="steps" aria-live="polite">
          <li v-for="(s, i) in steps.slice(0, stepsShown)" :key="s" class="step">
            <el-icon class="step-icon"><Check /></el-icon>
            <span>{{ s }}</span>
            <span v-if="i === stepsShown - 1 && playing" class="cursor" aria-hidden="true" />
          </li>
        </ol>
        <button v-if="playing" type="button" class="skip" @click="showAll">直接看全部</button>

        <el-empty v-if="!summary && !generating" :image-size="60">
          <template #description>
            <span v-if="view.canGenerate">点上面的「整理」，让大模型把下面抓到的通知整理成要点。</span>
            <span v-else>管理员整理后这里会显示要点。先看看下面的官方通知和信息渠道。</span>
          </template>
        </el-empty>

        <!-- 要点 -->
        <section v-if="summary && pointsShown > 0" class="block">
          <h3 class="sub">要点</h3>
          <TransitionGroup name="rise" tag="ul" class="points">
            <li v-for="p in summary.points.slice(0, pointsShown)" :key="p.label + p.text" class="point">
              <span class="label">{{ p.label }}</span>
              <div class="point-body">
                <div class="text">{{ p.text }}</div>
                <details class="quote">
                  <summary>看原文</summary>
                  <blockquote>{{ p.quote }}</blockquote>
                  <a v-if="safeUrl(p.sourceUrl)" :href="safeUrl(p.sourceUrl)!" target="_blank" rel="noopener noreferrer">
                    出自《{{ sourceLabel(p.sourceTitle) }}》↗
                  </a>
                </details>
              </div>
            </li>
          </TransitionGroup>
        </section>

        <template v-if="!playing">
          <p v-if="summary && view.missing.length" class="missing">
            没整理出 <b>{{ view.missing.join('、') }}</b>：通知里可能没写，或者平台还没抓到。请到下面的渠道查一查。
          </p>

          <!-- 更多信息渠道 -->
          <section class="block">
            <h3 class="sub">更多信息渠道</h3>
            <ul class="channels">
              <li v-for="c in view.channels" :key="c.name + (c.url ?? '')">
                <span class="channel-head">
                  <a v-if="safeUrl(c.url)" :href="safeUrl(c.url)!" target="_blank" rel="noopener noreferrer" class="channel-name">
                    {{ c.name }} ↗
                  </a>
                  <span v-else class="channel-name">{{ c.name }}</span>
                  <button v-if="c.copy" type="button" class="channel-action" @click="copyText(c.copy)">复制名称</button>
                  <button v-if="c.anchor" type="button" class="channel-action" @click="emit('jump', c.anchor)">去看看 →</button>
                </span>
                <span v-if="c.hint" class="channel-hint">{{ c.hint }}</span>
              </li>
            </ul>
          </section>

          <!-- 平台抓到的通知 -->
          <section class="block">
            <h3 class="sub">平台抓到的通知</h3>
            <p v-if="view.sources.length === 0" class="hint">还没有抓到这个比赛的通知。</p>
            <ul v-else class="sources">
              <li v-for="s in view.sources" :key="s.id">
                <a v-if="safeUrl(s.url)" :href="safeUrl(s.url)!" target="_blank" rel="noopener noreferrer">
                  <el-icon><Link /></el-icon>{{ sourceLabel(s.title) }}
                </a>
                <span v-else>{{ sourceLabel(s.title) }}</span>
                <span class="source-meta">
                  抓取于 {{ fmtDateTime(s.fetchedAt) }}
                  <el-tag v-if="s.used" size="small" effect="plain">用于这次整理</el-tag>
                  <el-tag v-if="!s.aiAllowed" size="small" type="info" effect="plain">网站不让 AI 读，只放链接</el-tag>
                </span>
              </li>
            </ul>
          </section>
        </template>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.page {
  position: relative;
  min-height: 200px;
  padding: 4px 4px 24px;
}

.close {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  padding: 4px;
  border: 0;
  background: none;
  color: var(--jd-muted);
  cursor: pointer;
}

.close:hover {
  color: var(--jd-ink);
}

.name {
  margin: 4px 32px 12px 0;
  font-size: 20px;
  font-weight: 700;
}

.warn {
  margin: 0 0 12px;
  padding: 8px 12px;
  border-left: 2px solid var(--jd-accent);
  background: var(--jd-accent-soft);
  font-size: 13px;
  line-height: 1.6;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--jd-muted);
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--jd-muted);
}

.steps {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.step {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  font-size: 13px;
  color: var(--jd-ink-2);
  animation: rise 0.25s ease-out;
}

.step-icon {
  flex: none;
  color: var(--jd-olive);
}

.cursor {
  width: 6px;
  height: 12px;
  background: var(--jd-accent);
  animation: blink 0.8s steps(2) infinite;
}

.skip {
  margin-top: 4px;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  color: var(--jd-muted);
  text-decoration: underline;
  cursor: pointer;
}

.block {
  margin-top: 18px;
}

.sub {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
}

.points,
.channels,
.sources {
  margin: 0;
  padding: 0;
  list-style: none;
}

.point {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-top: 1px solid var(--jd-line-lighter);
}

.label {
  flex: none;
  width: 72px;
  font-size: 12px;
  font-weight: 700;
  color: var(--jd-accent);
}

.point-body {
  flex: 1;
  min-width: 0;
}

.text {
  font-size: 14px;
  line-height: 1.6;
}

.quote {
  margin-top: 2px;
  font-size: 12px;
  color: var(--jd-muted);
}

.quote summary {
  cursor: pointer;
}

.quote blockquote {
  margin: 4px 0;
  padding: 4px 8px;
  border-left: 2px solid var(--jd-line);
  background: var(--jd-field);
  color: var(--jd-ink-2);
  line-height: 1.6;
}

.missing {
  margin: 14px 0 0;
  padding: 8px 12px;
  border-left: 2px solid var(--jd-red);
  background: var(--jd-red-soft);
  font-size: 13px;
  line-height: 1.6;
}

.channels li,
.sources li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 0;
  border-top: 1px solid var(--jd-line-lighter);
  font-size: 13px;
}

.channel-name {
  font-weight: 700;
}

.channel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.channel-action {
  padding: 0 6px;
  border: 1px solid var(--jd-line-strong);
  background: none;
  font: inherit;
  font-size: 11px;
  color: var(--jd-ink-2);
  cursor: pointer;
}

.channel-action:hover {
  border-color: var(--jd-accent);
  color: var(--jd-accent);
}

.channel-hint,
.source-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--jd-muted);
}

.sources a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.rise-enter-active {
  transition:
    opacity 0.25s ease-out,
    transform 0.25s ease-out;
}

.rise-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}
</style>

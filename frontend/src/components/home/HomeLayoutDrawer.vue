<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { HomeLayout } from '@/types/api'
import { COLUMN_TITLE, HOME_PANELS, defaultLayout, moveInColumn, type HomeColumn, type HomePanel } from './layout'

/**
 * 自定义主页：每个面板能显示 / 隐藏、在自己那一栏里上下挪，日历栏能加宽。
 * 抽屉不挡住主页（没有遮罩），改一下主页马上跟着变；保存由主页负责（存到账号里）。
 */
const props = defineProps<{ layout: HomeLayout }>()
const emit = defineEmits<{ change: [layout: HomeLayout] }>()
const open = defineModel<boolean>({ required: true })

const narrowQuery = window.matchMedia('(max-width: 560px)')
const narrow = ref(narrowQuery.matches)
const onNarrow = (e: MediaQueryListEvent) => (narrow.value = e.matches)
narrowQuery.addEventListener('change', onNarrow)
onBeforeUnmount(() => narrowQuery.removeEventListener('change', onNarrow))

const COLUMNS: HomeColumn[] = ['top', 'left', 'middle', 'right']

/** 按栏分组，每栏里按当前顺序 */
const groups = computed(() =>
  COLUMNS.map((column) => ({
    column,
    title: COLUMN_TITLE[column],
    panels: props.layout.order
      .map((k) => HOME_PANELS.find((p) => p.key === k))
      .filter((p): p is HomePanel => !!p && p.column === column),
  })),
)

const isHidden = (p: HomePanel) => props.layout.hidden.includes(p.key)

function move(key: string, dir: -1 | 1) {
  emit('change', { ...props.layout, order: moveInColumn(props.layout.order, key, dir) })
}

function toggle(p: HomePanel) {
  if (p.required) return
  const hidden = isHidden(p) ? props.layout.hidden.filter((k) => k !== p.key) : [...props.layout.hidden, p.key]
  emit('change', { ...props.layout, hidden })
}

function setWide(v: boolean) {
  emit('change', { ...props.layout, wideCalendar: v })
}

function reset() {
  emit('change', defaultLayout())
}
</script>

<template>
  <el-drawer
    v-model="open"
    title="自定义主页"
    :size="narrow ? '100%' : '340px'"
    :modal="false"
    :lock-scroll="false"
    class="home-layout-drawer"
    append-to-body
  >
    <p class="hint">改了马上生效，自动保存到你的账号，换台电脑登录也一样。</p>

    <section v-for="g in groups" :key="g.column" class="group">
      <h4 class="group-title">{{ g.title }}</h4>
      <div v-for="(p, i) in g.panels" :key="p.key" class="item" :class="{ off: isHidden(p) }">
        <span class="name">{{ p.label }}</span>
        <span v-if="g.panels.length > 1" class="moves">
          <el-button text size="small" :icon="ArrowUp" :disabled="i === 0" :aria-label="`${p.label}往上挪`" @click="move(p.key, -1)" />
          <el-button
            text
            size="small"
            :icon="ArrowDown"
            :disabled="i === g.panels.length - 1"
            :aria-label="`${p.label}往下挪`"
            @click="move(p.key, 1)"
          />
        </span>
        <el-switch
          :model-value="!isHidden(p)"
          :disabled="p.required"
          :aria-label="`显示${p.label}`"
          @update:model-value="toggle(p)"
        />
      </div>
      <p v-if="g.column === 'right'" class="note">「待你处理」不能隐藏，免得错过邀请和申请。</p>
      <div v-if="g.column === 'left'" class="item">
        <span class="name">日历栏加宽</span>
        <el-switch :model-value="layout.wideCalendar" aria-label="日历栏加宽" @update:model-value="(v) => setWide(!!v)" />
      </div>
    </section>

    <template #footer>
      <el-button @click="reset">恢复默认</el-button>
      <el-button type="primary" @click="open = false">完成</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--jd-muted);
}

.group {
  margin-bottom: 14px;
}

.group-title {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--jd-muted);
}

.item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  border-top: 1px solid var(--jd-line-lighter);
}

.name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.item.off .name {
  color: var(--jd-placeholder);
  text-decoration: line-through;
}

.moves {
  display: inline-flex;
}

.moves .el-button + .el-button {
  margin-left: 0;
}

.note {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--jd-muted);
}
</style>

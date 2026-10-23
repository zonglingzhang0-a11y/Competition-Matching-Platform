<script setup lang="ts">
import { computed } from 'vue'
import type { RadarAxis } from '@/types/api'
import { SKILL_LEVEL_LABEL } from '@/utils/labels'

/**
 * 雷达图下面的一块：把每个轴上的具体技能列出来。
 * 雷达图只有六个轴，看形状；这里回答"这个形状是哪些技能撑起来的"。
 * 没填具体技能的轴不是 0：按专业和相关技能估了基础分，这里会写清楚是按什么估的。
 */
const props = defineProps<{
  axes: RadarAxis[]
  mode?: 'person' | 'need'
  /** 只显示有技能的轴（队伍页的需求列表用，空轴不占地方） */
  onlyFilled?: boolean
}>()

const shown = computed(() =>
  props.onlyFilled ? props.axes.filter((a) => a.skills.length > 0) : props.axes,
)

function guessed(a: RadarAxis) {
  return a.basis === 'INFERRED' || a.basis === 'BASE'
}
</script>

<template>
  <div class="groups">
    <div v-for="a in shown" :key="a.key" class="group">
      <div class="head">
        <span class="label">{{ a.label }}</span>
        <span class="band" :class="{ guessed: guessed(a) }">{{ a.band }} {{ a.score }}</span>
        <span class="count num">{{ a.skills.length }}</span>
      </div>
      <div v-if="a.skills.length" class="chips">
        <span v-for="s in a.skills" :key="s.name" class="chip">
          {{ s.name }}<em>{{ mode === 'need' ? ' ≥ ' : ' · ' }}{{ SKILL_LEVEL_LABEL[s.level] }}</em>
        </span>
      </div>
      <div v-else-if="guessed(a)" class="none">
        没填具体技能，按 {{ a.band }} {{ a.score }} 估<template v-if="a.reason">：{{ a.reason }}</template>
      </div>
      <div v-else class="none">这一类还没填</div>
      <div v-if="mode === 'need' && a.roles.length" class="roles">缺的角色：{{ a.roles.join('、') }}</div>
    </div>
  </div>
</template>

<style scoped>
.groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px 18px;
}

.group {
  min-width: 0;
  padding: 8px 0;
  border-top: 1px solid var(--jd-line);
}

.head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}

.label {
  font-size: 13px;
  font-weight: 500;
}

.band,
.count {
  font-size: 12px;
  color: var(--jd-muted);
}

.band.guessed {
  color: #8c7b5a;
}

.count {
  margin-left: auto;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
}

.chip {
  padding: 1px 7px;
  border: 1px solid var(--jd-line);
  font-size: 12px;
  line-height: 1.7;
  color: var(--jd-ink-2);
}

.chip em {
  font-style: normal;
  color: var(--jd-muted);
}

.none {
  font-size: 12px;
  line-height: 1.6;
  color: var(--jd-muted);
}

.roles {
  margin-top: 4px;
  font-size: 12px;
  color: var(--jd-muted);
}
</style>

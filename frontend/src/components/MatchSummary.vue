<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChatDotRound, CircleCheck, Warning } from '@element-plus/icons-vue'
import type { MatchResult } from '@/types/api'

const props = withDefaults(defineProps<{ match: MatchResult; compact?: boolean }>(), { compact: false })

const showDims = ref(false)

const color = computed(() => {
  const s = props.match.score
  if (s >= 80) return 'var(--el-color-success)'
  if (s >= 60) return 'var(--el-color-primary)'
  return 'var(--el-color-warning)'
})

const reasons = computed(() => (props.compact ? props.match.reasons.slice(0, 2) : props.match.reasons))
</script>

<template>
  <div v-if="match.veto" class="veto">
    <el-icon><Warning /></el-icon>
    <span>不推荐：{{ match.veto }}</span>
  </div>
  <div v-else class="match" :class="{ compact }">
    <div class="score" :style="{ color, borderColor: color }" :aria-label="`匹配度 ${Math.round(match.score)}`">
      <span class="num">{{ Math.round(match.score) }}</span>
      <span class="unit">匹配度</span>
    </div>
    <div class="body">
      <div v-for="r in reasons" :key="r" class="line good">
        <el-icon><CircleCheck /></el-icon>
        <span>{{ r }}</span>
      </div>
      <div v-if="match.talkAbout" class="line talk">
        <el-icon><ChatDotRound /></el-icon>
        <span>{{ match.talkAbout }}</span>
      </div>
      <div v-for="w in match.warnings" :key="w" class="line warn">
        <el-icon><Warning /></el-icon>
        <span>{{ w }}</span>
      </div>
      <!-- 完整度为 0 时后端已经在 warnings 里说明了，这里不重复 -->
      <div v-if="match.completeness > 0 && match.completeness < 0.6" class="line muted">问卷还没填全，分数只供参考</div>
      <template v-if="!compact">
        <el-button text size="small" class="toggle" @click="showDims = !showDims">
          {{ showDims ? '收起' : '看各维度得分' }}
        </el-button>
        <div v-if="showDims" class="dims">
          <div v-for="d in match.dimensions" :key="d.key" class="dim">
            <span class="dim-name">{{ d.name }}</span>
            <el-progress
              v-if="d.score !== null"
              :percentage="Math.round(d.score)"
              :stroke-width="6"
              :show-text="true"
              class="dim-bar"
            />
            <span v-else class="muted dim-bar">数据不足</span>
          </div>
          <div v-if="match.k < 1" class="muted">承诺系数 × {{ match.k }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.match {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.score {
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 2px solid;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.1;
}

.compact .score {
  width: 48px;
  height: 48px;
}

.num {
  font-size: 18px;
  font-weight: 500;
}

.compact .num {
  font-size: 16px;
}

.unit {
  font-size: 10px;
  opacity: 0.8;
}

.body {
  min-width: 0;
  flex: 1;
}

.line {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 3px;
}

.line .el-icon {
  margin-top: 3px;
  flex: none;
}

.good {
  color: var(--el-text-color-regular);
}

.good .el-icon {
  color: var(--el-color-success);
}

.talk {
  color: var(--el-color-primary);
}

.warn {
  color: var(--el-color-warning);
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.toggle {
  padding: 0;
  margin-top: 2px;
}

.dims {
  margin-top: 6px;
  max-width: 360px;
}

.dim {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.dim-name {
  width: 72px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dim-bar {
  flex: 1;
}

.veto {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

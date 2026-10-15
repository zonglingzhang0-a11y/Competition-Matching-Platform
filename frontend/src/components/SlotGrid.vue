<script setup lang="ts">
/**
 * 一周时段格子：7 天 × 上午/下午/晚上。
 * 值是一个整数位图，第 day*3+seg 位表示那个时段有空（day 0 = 周一），和后端、问卷 G4 的编码一致。
 */
const model = defineModel<number>({ default: 0 })

withDefaults(defineProps<{ disabled?: boolean }>(), { disabled: false })

const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const SEGMENTS = ['上午', '下午', '晚上']

const bit = (day: number, seg: number) => 1 << (day * 3 + seg)
const isOn = (day: number, seg: number) => (model.value & bit(day, seg)) !== 0

function toggle(day: number, seg: number) {
  model.value = model.value ^ bit(day, seg)
}
</script>

<template>
  <div class="grid" role="group" aria-label="一周时段">
    <div />
    <div v-for="d in DAYS" :key="d" class="head">{{ d }}</div>
    <template v-for="(s, si) in SEGMENTS" :key="s">
      <div class="seg">{{ s }}</div>
      <button
        v-for="(d, di) in DAYS"
        :key="d"
        type="button"
        class="cell"
        :class="{ on: isOn(di, si) }"
        :disabled="disabled"
        :aria-pressed="isOn(di, si)"
        :aria-label="`${d}${s}`"
        @click="toggle(di, si)"
      />
    </template>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 44px repeat(7, minmax(32px, 1fr));
  gap: 4px;
  max-width: 420px;
}

.head,
.seg {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cell {
  height: 28px;
  border-radius: 2px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  cursor: pointer;
  transition: background 0.15s;
}

.cell:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
}

.cell.on {
  background: var(--el-color-primary-light-7);
  border-color: var(--el-color-primary);
}

.cell:disabled {
  cursor: not-allowed;
}
</style>

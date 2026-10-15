<script setup lang="ts">
import { Delete } from '@element-plus/icons-vue'
import type { EndMode } from '@/types/api'
import { NODE_TYPE_LABEL } from '@/utils/labels'
import type { NodeRow } from '@/utils/nodeRows'

/**
 * 一届比赛的官方时间节点列表，逐个编辑。
 * 时间不确定的节点（比如"成绩公布：11 月中下旬"）用"最晚开始"和"结束区间"表示，日历里显示成虚线框。
 */
const rows = defineModel<NodeRow[]>({ required: true })

const END_MODES: { value: EndMode; label: string }[] = [
  { value: 'FIXED', label: '确定' },
  { value: 'RANGE', label: '一个区间' },
  { value: 'OPEN', label: '没有结束' },
]

function remove(i: number) {
  rows.value.splice(i, 1)
}
</script>

<template>
  <div>
    <el-empty v-if="rows.length === 0" description="还没有节点" :image-size="60" />
    <div v-for="(n, i) in rows" :key="i" class="node" :class="{ highlight: n.fromNotice }">
      <div class="line">
        <span class="index">{{ i + 1 }}</span>
        <el-input v-model="n.title" maxlength="64" placeholder="节点名称，如 报名截止、比赛、成绩公布" class="title" />
        <el-select v-model="n.nodeType" class="type">
          <el-option v-for="(label, value) in NODE_TYPE_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button :icon="Delete" circle text aria-label="删除这个节点" @click="remove(i)" />
      </div>
      <div class="line">
        <span class="label">开始</span>
        <el-date-picker
          v-model="n.startAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="开始时间"
          class="picker"
        />
        <el-checkbox v-model="n.fuzzyStart">时间没定</el-checkbox>
        <template v-if="n.fuzzyStart">
          <span class="label">最晚</span>
          <el-date-picker
            v-model="n.startLatestAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="最晚什么时候开始"
            class="picker"
          />
        </template>
      </div>
      <div class="line">
        <span class="label">结束</span>
        <el-radio-group v-model="n.endMode" size="small">
          <el-radio-button v-for="m in END_MODES" :key="m.value" :value="m.value">{{ m.label }}</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="n.endMode !== 'OPEN'"
          v-model="n.endAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          :placeholder="n.endMode === 'RANGE' ? '最早' : '结束时间（可不填）'"
          class="picker"
        />
        <template v-if="n.endMode === 'RANGE'">
          <span class="label">到</span>
          <el-date-picker
            v-model="n.endLatestAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="最晚"
            class="picker"
          />
        </template>
      </div>
      <div class="line">
        <el-checkbox v-model="n.allDay">全天</el-checkbox>
        <el-checkbox v-model="n.busy">参与撞期检测</el-checkbox>
        <el-input v-model="n.note" maxlength="255" placeholder="备注（选填）" class="note" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.node {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 2px;
}

.node.highlight {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.index {
  width: 20px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.title {
  flex: 1;
  min-width: 200px;
}

.type {
  width: 100px;
}

.label {
  width: 28px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.picker {
  width: 190px;
}

.note {
  flex: 1;
  min-width: 200px;
}
</style>

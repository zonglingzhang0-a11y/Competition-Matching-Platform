<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { editionNodes, replaceNodes } from '@/api/admin'
import type { EndMode, NodeForm } from '@/types/api'
import { NODE_TYPE_LABEL } from '@/utils/labels'
import { showError } from '@/utils/feedback'

/**
 * 一届比赛的官方时间节点，整体编辑、一次保存。
 * 时间不确定的节点（比如"成绩公布：11 月中下旬"）用"最晚开始"和"结束区间"表示，日历里显示成虚线框。
 */
const props = defineProps<{ editionId: number | null; title: string }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>({ required: true })

const END_MODES: { value: EndMode; label: string }[] = [
  { value: 'FIXED', label: '确定' },
  { value: 'RANGE', label: '一个区间' },
  { value: 'OPEN', label: '没有结束' },
]

/** 编辑时额外记一下"开始时间不确定"的勾选状态 */
type Row = NodeForm & { fuzzyStart: boolean }

const rows = ref<Row[]>([])
const loading = ref(false)
const saving = ref(false)

watch(open, async (value) => {
  if (!value || props.editionId === null) return
  loading.value = true
  rows.value = []
  try {
    rows.value = (await editionNodes(props.editionId)).map((n) => ({
      title: n.title,
      nodeType: n.nodeType,
      startAt: n.startAt,
      endAt: n.endAt,
      allDay: n.allDay,
      startLatestAt: n.startLatestAt,
      endMode: n.endMode,
      endLatestAt: n.endLatestAt,
      busy: n.busy,
      note: n.note,
      fuzzyStart: !!n.startLatestAt,
    }))
  } catch (e) {
    showError(e)
    open.value = false
  } finally {
    loading.value = false
  }
})

function add() {
  rows.value.push({
    title: '',
    nodeType: 'OTHER',
    startAt: '',
    endAt: null,
    allDay: false,
    startLatestAt: null,
    endMode: 'FIXED',
    endLatestAt: null,
    busy: false,
    note: null,
    fuzzyStart: false,
  })
}

function remove(i: number) {
  rows.value.splice(i, 1)
}

async function save() {
  if (props.editionId === null) return
  const missing = rows.value.findIndex((r) => !r.title.trim() || !r.startAt)
  if (missing >= 0) {
    ElMessage.warning(`第 ${missing + 1} 个节点还没填名称或开始时间`)
    return
  }
  saving.value = true
  try {
    await replaceNodes(
      props.editionId,
      rows.value.map(({ fuzzyStart, ...n }) => ({
        ...n,
        title: n.title.trim(),
        note: n.note?.trim() || null,
        startLatestAt: fuzzyStart ? n.startLatestAt : null,
        endAt: n.endMode === 'OPEN' ? null : n.endAt,
        endLatestAt: n.endMode === 'RANGE' ? n.endLatestAt : null,
      })),
    )
    ElMessage.success('时间节点已保存')
    open.value = false
    emit('saved')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="`官方时间节点 · ${title}`" width="760px" top="6vh">
    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && rows.length === 0" description="还没有节点" :image-size="60" />
      <div v-for="(n, i) in rows" :key="i" class="node">
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
    <p class="tip">"参与撞期检测"的节点（一般是比赛本身）会和学生的其他比赛、个人日程比对；报名截止前 72 和 24 小时会提醒关注的人。</p>
    <template #footer>
      <el-button class="add" @click="add">+ 添加节点</el-button>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.list {
  min-height: 80px;
  max-height: 62vh;
  overflow-y: auto;
}

.node {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
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

.tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.add {
  float: left;
}
</style>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { editionNodes, replaceNodes } from '@/api/admin'
import NodeListEditor from '@/components/admin/NodeListEditor.vue'
import { emptyRow, firstIncomplete, toForms, toRow, type NodeRow } from '@/utils/nodeRows'
import { showError } from '@/utils/feedback'

/** 一届比赛的官方时间节点，整体编辑、一次保存 */
const props = defineProps<{ editionId: number | null; title: string }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>({ required: true })

const rows = ref<NodeRow[]>([])
const loading = ref(false)
const saving = ref(false)

watch(open, async (value) => {
  if (!value || props.editionId === null) return
  loading.value = true
  rows.value = []
  try {
    rows.value = (await editionNodes(props.editionId)).map(toRow)
  } catch (e) {
    showError(e)
    open.value = false
  } finally {
    loading.value = false
  }
})

async function save() {
  if (props.editionId === null) return
  const missing = firstIncomplete(rows.value)
  if (missing >= 0) {
    ElMessage.warning(`第 ${missing + 1} 个节点还没填名称或开始时间`)
    return
  }
  saving.value = true
  try {
    await replaceNodes(props.editionId, toForms(rows.value))
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
      <NodeListEditor v-if="!loading" v-model="rows" />
    </div>
    <p class="tip">"参与撞期检测"的节点（一般是比赛本身）会和学生的其他比赛、个人日程比对；报名截止前 72 和 24 小时会提醒关注的人。</p>
    <template #footer>
      <el-button class="add" @click="rows.push(emptyRow())">+ 添加节点</el-button>
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

.tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.add {
  float: left;
}
</style>

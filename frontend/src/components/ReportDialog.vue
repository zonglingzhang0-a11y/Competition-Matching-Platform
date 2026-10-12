<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createReport } from '@/api/review'
import type { ReportReason, ReportTarget } from '@/types/api'
import { REPORT_REASON } from '@/utils/labels'
import { showError } from '@/utils/feedback'

/** 举报一个人或一支队伍。管理员核实后会通过站内信告诉你结果 */
const props = defineProps<{ targetType: ReportTarget; targetId: number | null; targetName: string }>()
const open = defineModel<boolean>({ required: true })

const form = reactive<{ reason: ReportReason | ''; detail: string }>({ reason: '', detail: '' })
const saving = ref(false)

watch(open, (value) => {
  if (value) Object.assign(form, { reason: '', detail: '' })
})

async function submit() {
  if (!form.reason || props.targetId === null) {
    ElMessage.warning('请选择举报原因')
    return
  }
  saving.value = true
  try {
    await createReport({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: form.reason,
      detail: form.detail.trim(),
    })
    ElMessage.success('已提交，管理员核实后会通知你结果')
    open.value = false
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="`举报${targetType === 'TEAM' ? '队伍' : ''}「${targetName}」`" width="460px">
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="原因" required>
        <el-radio-group v-model="form.reason">
          <el-radio v-for="(label, value) in REPORT_REASON" :key="value" :value="value">{{ label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="发生了什么、在哪里看到的，越具体越好核实"
        />
      </el-form-item>
    </el-form>
    <p class="tip">核实属实的，对方会被暂停推荐或停用账号。恶意举报也会记录在案。</p>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="danger" :loading="saving" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

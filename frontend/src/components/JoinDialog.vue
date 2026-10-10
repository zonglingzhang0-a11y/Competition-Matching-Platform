<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { NeedView } from '@/types/api'

/**
 * 申请加入和邀请别人共用：选一个招募需求（可不选），写一句留言。
 */
const props = defineProps<{
  title: string
  /** 被邀请的人，邀请时显示 */
  target?: string
  needs: NeedView[]
  submit: (needId: number | null, message: string) => Promise<void>
}>()

const open = defineModel<boolean>({ required: true })

/** 0 表示"都不是" */
const needId = ref(0)
const message = ref('')
const saving = ref(false)
const openNeeds = computed(() => props.needs.filter((n) => n.status === 'OPEN'))

watch(open, (value) => {
  if (!value) return
  needId.value = openNeeds.value.length === 1 ? openNeeds.value[0]!.id : 0
  message.value = ''
})

async function onSubmit() {
  saving.value = true
  try {
    await props.submit(needId.value || null, message.value.trim())
    open.value = false
  } catch {
    // 错误提示由调用方负责
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="title" width="460px">
    <p v-if="target" class="target">邀请 {{ target }}</p>
    <el-form label-position="top" @submit.prevent="onSubmit">
      <el-form-item v-if="openNeeds.length" label="对应哪个招募需求">
        <el-radio-group v-model="needId" class="needs">
          <el-radio v-for="n in openNeeds" :key="n.id" :value="n.id">
            {{ n.roleName }}（还缺 {{ n.headcount - n.filled }} 人）
          </el-radio>
          <el-radio :value="0">都不是</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="留言">
        <el-input
          v-model="message"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="介绍一下自己能做什么、为什么想一起参赛"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">发送</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.target {
  margin: 0 0 12px;
  font-size: 14px;
}

.needs {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
</style>

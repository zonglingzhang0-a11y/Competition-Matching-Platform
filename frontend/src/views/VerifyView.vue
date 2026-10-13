<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import SendCodeButton from '@/components/SendCodeButton.vue'
import { confirmEdu, latestVerify, sendEduCode } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { LatestVerifyRequest } from '@/types/api'
import { showError } from '@/utils/feedback'

const user = useUserStore()

/** 最近一次申请，没通过时显示原因 */
const latest = ref<LatestVerifyRequest | null>(null)

async function loadLatest() {
  try {
    latest.value = await latestVerify()
  } catch {
    // 拿不到就不显示原因，不影响认证
  }
}

onMounted(loadLatest)

const formRef = ref<FormInstance>()
const form = reactive({ eduEmail: '', code: '' })
const loading = ref(false)

const rules: FormRules<typeof form> = {
  eduEmail: [
    { required: true, message: '请填写教育邮箱', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) =>
        /@([a-z0-9-]+\.)+edu\.cn$/i.test(value.trim()) ? callback() : callback(new Error('请使用学校发的 .edu.cn 邮箱')),
      trigger: 'blur',
    },
  ],
  code: [{ required: true, message: '请填写验证码', trigger: 'blur' }],
}

const status = computed(() => user.me?.verifyStatus ?? 'NONE')

async function sendCode() {
  const ok = await formRef.value?.validateField('eduEmail').then(() => true, () => false)
  if (!ok) throw new Error('请先填写正确的教育邮箱')
  await sendEduCode(form.eduEmail.trim())
  ElMessage.success('验证码已发到你的教育邮箱')
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    const result = await confirmEdu(form.eduEmail.trim(), form.code.trim())
    if (result.verifyStatus === 'VERIFIED') ElMessage.success(result.message)
    else ElMessage.info(result.message)
    await user.refreshMe()
    await loadLatest()
    form.code = ''
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="verify">
    <el-card shadow="never" class="block">
      <template #header>认证状态</template>
      <el-result
        v-if="status === 'VERIFIED'"
        icon="success"
        :title="`已认证 · ${user.me?.school?.name ?? ''}`"
        :sub-title="`有效期到 ${user.me?.verifiedUntil}，下一学年需要重新认证`"
      />
      <el-alert
        v-else-if="status === 'PENDING'"
        type="info"
        :closable="false"
        show-icon
        title="邮箱已验证，学校待确认"
        description="平台还没收录你所在学校的邮箱后缀，管理员确认后会通知你。"
      />
      <el-alert
        v-else-if="latest?.status === 'REJECTED'"
        type="error"
        :closable="false"
        show-icon
        :title="`${latest.eduEmail ?? '上次的申请'} 没有通过审核`"
        :description="`原因：${latest.reviewNote ?? '未说明'}。可以换一个学校邮箱重新认证。`"
      />
      <el-alert
        v-else
        type="warning"
        :closable="false"
        show-icon
        title="还没有认证"
        description="认证后才能建队和申请入队。没认证也可以先浏览比赛、填问卷。"
      />
      <el-alert
        v-if="status === 'VERIFIED' && latest?.status === 'PENDING'"
        type="info"
        :closable="false"
        show-icon
        class="pending-change"
        :title="`你提交的 ${latest.eduEmail ?? '新邮箱'} 在等管理员确认学校`"
        description="通过后会换成新学校；在那之前保留现在的认证，建队、申请都不受影响。"
      />
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>{{ status === 'VERIFIED' ? '换一个教育邮箱重新认证' : '教育邮箱认证' }}</template>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="教育邮箱" prop="eduEmail">
          <el-input v-model="form.eduEmail" placeholder="比如 2023xxxx@stu.xxx.edu.cn" />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" maxlength="6" placeholder="6 位数字" />
            <SendCodeButton :send="sendCode" />
          </div>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">完成认证</el-button>
      </el-form>
      <p class="hint">一个教育邮箱只能认证一个账号。没有教育邮箱的同学，后续会开放学生证截图认证。</p>
    </el-card>
  </div>
</template>

<style scoped>
.verify {
  max-width: 560px;
  margin: 0 auto;
}

.block {
  border-radius: 12px;
  margin-bottom: 16px;
}

.pending-change {
  margin-top: 12px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.hint {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

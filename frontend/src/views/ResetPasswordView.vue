<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthCard from '@/components/AuthCard.vue'
import SendCodeButton from '@/components/SendCodeButton.vue'
import { resetPassword, sendResetCode } from '@/api/auth'
import { PASSWORD_PATTERN, showError } from '@/utils/feedback'

/**
 * 忘记密码：邮箱验证码 → 新密码。设好后这个账号在所有设备上的登录都会失效，用新密码重新登录。
 */
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const form = reactive({
  email: typeof route.query.email === 'string' ? route.query.email : '',
  code: '',
  password: '',
  confirm: '',
})
const loading = ref(false)

const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请填写验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请填写新密码', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: '密码要 8 到 64 位，同时包含字母和数字', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再输一次新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) =>
        value === form.password ? callback() : callback(new Error('两次输入的密码不一样')),
      trigger: 'blur',
    },
  ],
}

/** 不管邮箱有没有注册过都提示一样的话，免得被拿来试哪些邮箱注册过 */
async function sendCode() {
  const ok = await formRef.value?.validateField('email').then(() => true, () => false)
  if (!ok) throw new Error('请先填写正确的邮箱')
  await sendResetCode(form.email)
  ElMessage.success('如果这个邮箱注册过，验证码已经发过去了，请查收邮件')
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await resetPassword({ email: form.email, code: form.code, password: form.password })
    ElMessage.success('密码已经重设，请用新密码登录')
    router.replace({ name: 'login', query: { email: form.email } })
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="重设密码" subtitle="用注册邮箱收验证码，设一个新密码">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="注册邮箱" prop="email">
        <el-input v-model="form.email" placeholder="name@example.com" autocomplete="username" />
      </el-form-item>
      <el-form-item label="验证码" prop="code">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="6 位数字" />
          <SendCodeButton :send="sendCode" />
        </div>
      </el-form-item>
      <el-form-item label="新密码" prop="password">
        <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirm">
        <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <p class="note">设好以后，这个账号在其他设备上的登录都会退出。</p>
      <el-button type="primary" native-type="submit" :loading="loading" class="submit">重设密码</el-button>
    </el-form>
    <template #footer>
      想起来了？<router-link :to="{ name: 'login', query: form.email ? { email: form.email } : {} }">登录</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.note {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.submit {
  width: 100%;
  margin-top: 8px;
}
</style>

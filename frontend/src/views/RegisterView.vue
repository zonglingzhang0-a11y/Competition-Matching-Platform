<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthCard from '@/components/AuthCard.vue'
import SendCodeButton from '@/components/SendCodeButton.vue'
import { sendRegisterCode } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { PASSWORD_PATTERN, showError } from '@/utils/feedback'

const router = useRouter()
const user = useUserStore()

const formRef = ref<FormInstance>()
const form = reactive({ email: '', code: '', nickname: '', password: '', confirm: '' })
const loading = ref(false)

const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请填写验证码', trigger: 'blur' }],
  nickname: [
    { required: true, message: '请填写昵称', trigger: 'blur' },
    { max: 16, message: '昵称最多 16 个字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请填写密码', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: '密码要 8 到 64 位，同时包含字母和数字', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再输一次密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) =>
        value === form.password ? callback() : callback(new Error('两次输入的密码不一样')),
      trigger: 'blur',
    },
  ],
}

/** 先单独校验邮箱，通过了才发验证码 */
async function sendCode() {
  const ok = await formRef.value?.validateField('email').then(() => true, () => false)
  if (!ok) throw new Error('请先填写正确的邮箱')
  await sendRegisterCode(form.email)
  ElMessage.success('验证码已发送，请查收邮件')
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await user.register({ email: form.email, code: form.code, nickname: form.nickname, password: form.password })
    ElMessage.success('注册成功')
    router.replace({ name: 'verify' })
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="注册" subtitle="注册后用学校邮箱认证，就能建队和申请入队">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="name@example.com" autocomplete="username" />
      </el-form-item>
      <el-form-item label="验证码" prop="code">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="6 位数字" />
          <SendCodeButton :send="sendCode" />
        </div>
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="form.nickname" maxlength="16" placeholder="队友会看到这个名字" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirm">
        <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" class="submit">注册</el-button>
    </el-form>
    <template #footer>
      已经有账号了？<router-link to="/login">登录</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.submit {
  width: 100%;
  margin-top: 8px;
}
</style>

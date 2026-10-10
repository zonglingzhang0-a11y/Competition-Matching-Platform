<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import AuthCard from '@/components/AuthCard.vue'
import { useUserStore } from '@/stores/user'
import { showError } from '@/utils/feedback'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const formRef = ref<FormInstance>()
const form = reactive({ email: '', password: '' })
const loading = ref(false)

const rules: FormRules<typeof form> = {
  email: [{ required: true, message: '请填写邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请填写密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await user.login(form.email, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="登录" subtitle="找到能一起冲奖的队友">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="name@example.com" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" class="submit">登录</el-button>
    </el-form>
    <template #footer>
      还没有账号？<router-link to="/register">注册</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
.submit {
  width: 100%;
  margin-top: 8px;
}
</style>

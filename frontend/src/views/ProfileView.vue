<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import ExperienceList from '@/components/ExperienceList.vue'
import SkillEditor from '@/components/SkillEditor.vue'
import { getProfile, updateProfile } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import type { Profile, ProfileForm } from '@/types/api'
import { DEGREES, GENDERS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

const user = useUserStore()

const profile = ref<Profile | null>(null)
const loading = ref(true)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ProfileForm>({
  nickname: '',
  realName: '',
  gender: '',
  major: '',
  enrollYear: null,
  degree: '',
  bio: '',
  contactWechat: '',
  contactQq: '',
})

const rules: FormRules<ProfileForm> = {
  nickname: [
    { required: true, message: '请填写昵称', trigger: 'blur' },
    { max: 16, message: '昵称最多 16 个字', trigger: 'blur' },
  ],
  contactQq: [{ pattern: /^\d{5,12}$/, message: 'QQ 号应该是 5 到 12 位数字', trigger: 'blur' }],
}

const currentYear = new Date().getFullYear()
const years = Array.from({ length: 10 }, (_, i) => currentYear - i)

async function load() {
  loading.value = true
  try {
    const p = await getProfile()
    profile.value = p
    Object.assign(form, {
      nickname: p.nickname,
      realName: p.realName ?? '',
      gender: p.gender ?? '',
      major: p.major ?? '',
      enrollYear: p.enrollYear,
      degree: p.degree ?? '',
      bio: p.bio ?? '',
      contactWechat: p.contactWechat ?? '',
      contactQq: p.contactQq ?? '',
    })
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await updateProfile(form)
    await user.refreshMe()
    ElMessage.success('资料已保存')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader en="PROFILE" zh="我的档案" />
    <template v-if="profile">
      <el-card shadow="never" class="block">
        <template #header>基本资料</template>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="form" @submit.prevent="save">
          <el-form-item label="邮箱">
            <span class="readonly">{{ profile.email }}</span>
          </el-form-item>
          <el-form-item label="学校">
            <span class="readonly">{{ profile.school?.name ?? '未认证' }}</span>
            <router-link v-if="profile.verifyStatus !== 'VERIFIED'" to="/verify" class="inline-link">去认证</router-link>
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" maxlength="16" />
          </el-form-item>
          <el-form-item label="专业">
            <el-input v-model="form.major" maxlength="64" placeholder="比如 计算机科学与技术" />
          </el-form-item>
          <el-form-item label="入学年份">
            <el-select v-model="form.enrollYear" clearable placeholder="选择年份" class="short">
              <el-option v-for="y in years" :key="y" :label="`${y} 级`" :value="y" />
            </el-select>
            <el-select v-model="form.degree" clearable placeholder="学历" class="short gap">
              <el-option v-for="d in DEGREES" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="form.gender">
              <el-radio v-for="g in GENDERS" :key="g.value" :value="g.value">{{ g.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="个人简介">
            <el-input
              v-model="form.bio"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="想参加什么方向的比赛、擅长什么，让队长更快了解你"
            />
          </el-form-item>

          <el-divider content-position="left">只有队友能看到</el-divider>
          <el-form-item label="真实姓名">
            <el-input v-model="form.realName" maxlength="32" placeholder="报名时要用" />
          </el-form-item>
          <el-form-item label="微信">
            <el-input v-model="form.contactWechat" maxlength="64" />
          </el-form-item>
          <el-form-item label="QQ" prop="contactQq">
            <el-input v-model="form.contactQq" maxlength="12" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="saving">保存资料</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>技能</template>
        <SkillEditor :initial="profile.skills" />
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>参赛履历</template>
        <ExperienceList :initial="profile.experiences" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  min-height: 200px;
}

.block {
  border-radius: 2px;
  margin-bottom: 16px;
}

.form {
  max-width: 560px;
}

.readonly {
  color: var(--el-text-color-regular);
}

.inline-link {
  margin-left: 12px;
  font-size: 13px;
}

.short {
  width: 140px;
}

.gap {
  margin-left: 12px;
}
</style>

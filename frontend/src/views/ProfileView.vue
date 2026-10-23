<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import ExperienceList from '@/components/ExperienceList.vue'
import SkillEditor from '@/components/SkillEditor.vue'
import { getProfile, setHistoryVisibility, updateProfile } from '@/api/profile'
import { useUserPageStore } from '@/stores/userPage'
import { useUserStore } from '@/stores/user'
import type { HistoryVisibility, Profile, ProfileForm } from '@/types/api'
import { DEGREES, GENDERS, HISTORY_VISIBILITY } from '@/utils/labels'
import { showError } from '@/utils/feedback'

const user = useUserStore()
const userPage = useUserPageStore()

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

/** 个人资料页上的履历给谁看，选了就保存 */
const savingPrivacy = ref(false)
async function changeVisibility(v: HistoryVisibility) {
  if (!profile.value) return
  const before = profile.value.historyVisibility
  profile.value.historyVisibility = v
  savingPrivacy.value = true
  try {
    await setHistoryVisibility(v)
    ElMessage.success('可见范围已保存')
  } catch (e) {
    profile.value.historyVisibility = before
    showError(e)
  } finally {
    savingPrivacy.value = false
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
            <span class="readonly">{{ profile.school?.name ?? (user.isAdmin ? '管理员不用认证学校' : '未认证') }}</span>
            <router-link v-if="profile.verifyStatus !== 'VERIFIED' && !user.isAdmin" to="/verify" class="inline-link">去认证</router-link>
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

      <el-card shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span>个人资料页</span>
            <el-button v-if="user.me" size="small" @click="userPage.open(user.me.id, { preview: true })">预览别人看到的我</el-button>
          </div>
        </template>
        <p class="privacy-intro">
          别人点你的名字，会看到你的个人资料页：技能、简介、组队记录和参赛履历。真实姓名和联系方式只给队友看。
        </p>
        <div class="privacy-label">参赛履历和组队记录给谁看</div>
        <el-radio-group
          :model-value="profile.historyVisibility"
          :disabled="savingPrivacy"
          class="privacy"
          @update:model-value="(v) => changeVisibility(v as HistoryVisibility)"
        >
          <el-radio v-for="o in HISTORY_VISIBILITY" :key="o.value" :value="o.value">
            <span class="privacy-option">{{ o.label }}</span>
            <span class="privacy-desc">{{ o.desc }}</span>
          </el-radio>
        </el-radio-group>
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

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.privacy-intro {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--jd-ink-2);
}

.privacy-label {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}

.privacy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

/* 说明文字长，手机上要能换行（Element Plus 的单选默认不换行） */
.privacy :deep(.el-radio) {
  height: auto;
  max-width: 100%;
  padding: 4px 0;
  align-items: flex-start;
  white-space: normal;
}

.privacy :deep(.el-radio__input) {
  margin-top: 3px;
}

.privacy :deep(.el-radio__label) {
  line-height: 1.5;
  white-space: normal;
}

.privacy-option {
  font-size: 14px;
}

.privacy-desc {
  margin-left: 8px;
  font-size: 12px;
  color: var(--jd-muted);
}
</style>

import { del, get, post, put } from './http'
import type {
  Experience,
  ExperienceForm,
  HistoryVisibility,
  Profile,
  ProfileForm,
  SkillLevel,
  SkillOption,
  UserPage,
} from '@/types/api'

export const getProfile = () => get<Profile>('/profile')

export const updateProfile = (form: ProfileForm) => put<void>('/profile', form)

export const skillOptions = () => get<SkillOption[]>('/skills')

export const replaceSkills = (skills: { skillId: number; level: number }[]) =>
  put<SkillLevel[]>('/profile/skills', { skills })

export const addExperience = (form: ExperienceForm) => post<Experience>('/profile/experiences', form)

export const deleteExperience = (id: number) => del<void>(`/profile/experiences/${id}`)

/** 某个人的个人资料页；看自己时 preview=true 返回其他同学看到的样子 */
export const getUserPage = (userId: number, preview = false) => get<UserPage>(`/users/${userId}/profile`, { preview })

export const setHistoryVisibility = (historyVisibility: HistoryVisibility) =>
  put<void>('/profile/privacy', { historyVisibility })

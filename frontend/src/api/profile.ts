import { del, get, post, put } from './http'
import type { Experience, ExperienceForm, Profile, ProfileForm, SkillLevel, SkillOption } from '@/types/api'

export const getProfile = () => get<Profile>('/profile')

export const updateProfile = (form: ProfileForm) => put<void>('/profile', form)

export const skillOptions = () => get<SkillOption[]>('/skills')

export const replaceSkills = (skills: { skillId: number; level: number }[]) =>
  put<SkillLevel[]>('/profile/skills', { skills })

export const addExperience = (form: ExperienceForm) => post<Experience>('/profile/experiences', form)

export const deleteExperience = (id: number) => del<void>(`/profile/experiences/${id}`)

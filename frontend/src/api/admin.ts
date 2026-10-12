import { del, get, post, put } from './http'
import type {
  AdminCompetitionRow,
  AdminUserRow,
  ApproveForm,
  ApproveResult,
  CompetitionForm,
  CompetitionNode,
  ConfigPage,
  EditionForm,
  EditionStat,
  MatchPreset,
  NodeForm,
  Overview,
  PageResult,
  ReviewItem,
  ReviewStatus,
  RoleTemplate,
  SchoolForm,
  SchoolRow,
  SkillGap,
  UserRole,
  UserStatus,
} from '@/types/api'

// ---------- 认证审核、学校 ----------

export const listReviews = (status: ReviewStatus | undefined, page: number) =>
  get<PageResult<ReviewItem>>('/admin/verifications', { status, page, size: 20 })

export const pendingReviewCount = () => get<number>('/admin/verifications/pending-count')

export const approveReview = (id: number, form: ApproveForm) =>
  post<ApproveResult>(`/admin/verifications/${id}/approve`, form)

export const rejectReview = (id: number, note: string) => post<void>(`/admin/verifications/${id}/reject`, { note })

export const listSchools = (keyword: string, page: number, size = 20) =>
  get<PageResult<SchoolRow>>('/admin/schools', { keyword: keyword || undefined, page, size })

export const createSchool = (form: SchoolForm) => post<number>('/admin/schools', form)

/** 返回顺带自动通过的待审核申请条数 */
export const addSchoolDomain = (schoolId: number, domain: string) =>
  post<number>(`/admin/schools/${schoolId}/domains`, { domain })

export const removeSchoolDomain = (domainId: number) => del<void>(`/admin/school-domains/${domainId}`)

// ---------- 账号 ----------

export const listUsers = (q: { keyword?: string; role?: UserRole; status?: UserStatus; page: number }) =>
  get<PageResult<AdminUserRow>>('/admin/users', { ...q, size: 20 })

export const setUserStatus = (id: number, status: UserStatus) => put<void>(`/admin/users/${id}/status`, { status })

// ---------- 比赛库 ----------

export const adminCompetitions = (keyword: string, page: number) =>
  get<PageResult<AdminCompetitionRow>>('/admin/competitions', { keyword: keyword || undefined, page, size: 10 })

export const roleTemplates = () => get<RoleTemplate[]>('/admin/role-templates')

export const createCompetition = (form: CompetitionForm) => post<number>('/admin/competitions', form)

export const updateCompetition = (id: number, form: CompetitionForm) => put<void>(`/admin/competitions/${id}`, form)

export const deleteCompetition = (id: number) => del<void>(`/admin/competitions/${id}`)

export const createEdition = (competitionId: number, form: EditionForm) =>
  post<number>(`/admin/competitions/${competitionId}/editions`, form)

export const updateEdition = (id: number, form: EditionForm) => put<void>(`/admin/editions/${id}`, form)

export const deleteEdition = (id: number) => del<void>(`/admin/editions/${id}`)

export const editionNodes = (id: number) => get<CompetitionNode[]>(`/admin/editions/${id}/nodes`)

export const replaceNodes = (id: number, nodes: NodeForm[]) => put<void>(`/admin/editions/${id}/nodes`, { nodes })

// ---------- 看板 ----------

export const dashboardOverview = () => get<Overview>('/admin/dashboard/overview')

export const dashboardEditions = () => get<EditionStat[]>('/admin/dashboard/editions')

export const skillGap = (editionId?: number) => get<SkillGap[]>('/admin/dashboard/skill-gap', { editionId })

// ---------- 匹配配置 ----------

export const matchConfigs = () => get<ConfigPage>('/admin/match-configs')

export const saveMatchConfig = (preset: MatchPreset, config: string) =>
  put<void>(`/admin/match-configs/${preset}`, { config })

import { del, get, getBlob, post, put } from './http'
import type {
  AdminCompetitionRow,
  AdminUserRow,
  ApproveForm,
  ApproveResult,
  CompetitionForm,
  CompetitionNode,
  ConfigPage,
  CrawlApproveForm,
  CrawlRecordDetail,
  CrawlRecordRow,
  CrawlRunStatus,
  CrawlSourceForm,
  CrawlSourceRow,
  EditionForm,
  EditionStat,
  FlaggedRow,
  MatchPreset,
  NodeForm,
  Overview,
  PageResult,
  PreviewResult,
  ReportAction,
  ReportRow,
  ReportStatus,
  ReviewItem,
  ReviewStatus,
  RoleTemplate,
  SchoolForm,
  SchoolRow,
  SkillGap,
  SuspendedRow,
  UserRole,
  UserStatus,
} from '@/types/api'

// ---------- 认证审核、学校 ----------

export const listReviews = (status: ReviewStatus | undefined, page: number) =>
  get<PageResult<ReviewItem>>('/admin/verifications', { status, page, size: 20 })

export const pendingReviewCount = () => get<number>('/admin/verifications/pending-count')

export const approveReview = (id: number, form: ApproveForm) =>
  post<ApproveResult>(`/admin/verifications/${id}/approve`, form)

/** 截图认证的原图，审核完会删掉 */
export const reviewImage = (id: number) => getBlob(`/admin/verifications/${id}/image`)

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

// ---------- 举报与信用 ----------

export const listReports = (status: ReportStatus | undefined, page: number) =>
  get<PageResult<ReportRow>>('/admin/reports', { status, page, size: 20 })

export const pendingReportCount = () => get<number>('/admin/reports/pending-count')

export const handleReport = (id: number, form: { result: ReportStatus; action: ReportAction; note: string }) =>
  post<void>(`/admin/reports/${id}/handle`, form)

export const suspendedUsers = () => get<SuspendedRow[]>('/admin/credit/suspended')

export const liftSuspension = (userId: number) => post<void>(`/admin/credit/${userId}/lift`)

export const flaggedReviews = () => get<FlaggedRow[]>('/admin/reviews/flagged')

// ---------- 匹配配置 ----------

export const matchConfigs = () => get<ConfigPage>('/admin/match-configs')

export const saveMatchConfig = (preset: MatchPreset, config: string) =>
  put<void>(`/admin/match-configs/${preset}`, { config })

// ---------- 通知抓取 ----------

export const crawlRecords = (tab: 'PENDING' | 'DONE', page: number) =>
  get<PageResult<CrawlRecordRow>>('/admin/crawl/records', { tab, page, size: 20 })

export const pendingCrawlCount = () => get<number>('/admin/crawl/records/pending-count')

export const crawlRecord = (id: number) => get<CrawlRecordDetail>(`/admin/crawl/records/${id}`)

/** 返回生成或更新的届次 id */
export const approveCrawl = (id: number, form: CrawlApproveForm) => post<number>(`/admin/crawl/records/${id}/approve`, form)

export const ignoreCrawl = (id: number) => post<void>(`/admin/crawl/records/${id}/ignore`)

export const crawlSources = () => get<CrawlSourceRow[]>('/admin/crawl/sources')

export const createCrawlSource = (form: CrawlSourceForm) => post<number>('/admin/crawl/sources', form)

export const updateCrawlSource = (id: number, form: CrawlSourceForm) => put<void>(`/admin/crawl/sources/${id}`, form)

export const deleteCrawlSource = (id: number) => del<void>(`/admin/crawl/sources/${id}`)

export const previewCrawlSource = (form: CrawlSourceForm) => post<PreviewResult>('/admin/crawl/sources/preview', form)

export const runCrawl = (sourceId?: number) =>
  post<void>(sourceId === undefined ? '/admin/crawl/run' : `/admin/crawl/sources/${sourceId}/run`)

export const crawlStatus = () => get<CrawlRunStatus>('/admin/crawl/status')

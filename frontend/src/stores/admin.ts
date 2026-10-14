import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { pendingCrawlCount, pendingReportCount, pendingReviewCount } from '@/api/admin'

/** 管理员菜单上的待办数（待审核认证、待处理举报、待确认的抓取通知）。处理完一条后调用 refresh，菜单马上跟着变 */
export const useAdminStore = defineStore('admin', () => {
  const pendingReviews = ref(0)
  const pendingReports = ref(0)
  const pendingCrawl = ref(0)
  const pendingTotal = computed(() => pendingReviews.value + pendingReports.value + pendingCrawl.value)

  /** 三个数各拿各的，一个拿不到（比如后端还没升级）不影响另外两个；角标拿不到也不影响使用 */
  async function refresh() {
    const [reviews, reports, crawl] = await Promise.allSettled([
      pendingReviewCount(),
      pendingReportCount(),
      pendingCrawlCount(),
    ])
    if (reviews.status === 'fulfilled') pendingReviews.value = reviews.value
    if (reports.status === 'fulfilled') pendingReports.value = reports.value
    if (crawl.status === 'fulfilled') pendingCrawl.value = crawl.value
  }

  return { pendingReviews, pendingReports, pendingCrawl, pendingTotal, refresh }
})

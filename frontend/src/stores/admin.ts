import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { pendingReportCount, pendingReviewCount } from '@/api/admin'

/** 管理员菜单上的待办数（待审核认证、待处理举报）。处理完一条后调用 refresh，菜单马上跟着变 */
export const useAdminStore = defineStore('admin', () => {
  const pendingReviews = ref(0)
  const pendingReports = ref(0)
  const pendingTotal = computed(() => pendingReviews.value + pendingReports.value)

  async function refresh() {
    try {
      ;[pendingReviews.value, pendingReports.value] = await Promise.all([pendingReviewCount(), pendingReportCount()])
    } catch {
      // 角标拿不到不影响使用
    }
  }

  return { pendingReviews, pendingReports, pendingTotal, refresh }
})

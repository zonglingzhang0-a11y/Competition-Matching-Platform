import { ref } from 'vue'
import { defineStore } from 'pinia'
import { pendingReviewCount } from '@/api/admin'

/** 管理员菜单上的待审核数。审核页处理完一条后调用 refresh，菜单马上跟着变 */
export const useAdminStore = defineStore('admin', () => {
  const pendingReviews = ref(0)

  async function refresh() {
    try {
      pendingReviews.value = await pendingReviewCount()
    } catch {
      // 角标拿不到不影响使用
    }
  }

  return { pendingReviews, refresh }
})

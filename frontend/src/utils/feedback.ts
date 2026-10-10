import { ElMessage } from 'element-plus'

/** 把接口错误提示给用户 */
export function showError(e: unknown) {
  ElMessage.error(e instanceof Error ? e.message : '操作失败，请稍后再试')
}

/** 和后端保持一致：8 到 64 位，同时包含字母和数字 */
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/

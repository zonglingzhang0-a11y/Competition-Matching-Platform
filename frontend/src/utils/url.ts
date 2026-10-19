// 用户填的链接（履历证明、学校文件）只认 http 和 https：javascript: 这类链接点开会在本站执行脚本

const HTTP_URL = /^https?:\/\/\S+$/i

/** 能放进 href 的链接；不是 http(s) 的返回 null，页面上就不显示成链接 */
export function safeUrl(url: string | null | undefined): string | null {
  const u = url?.trim()
  return u && HTTP_URL.test(u) ? u : null
}

/** 表单校验用：空的算合格（链接都是选填） */
export function isHttpUrlOrEmpty(url: string | null | undefined): boolean {
  return !url?.trim() || safeUrl(url) !== null
}

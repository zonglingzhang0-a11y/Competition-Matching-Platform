// 链接过滤的测试：npm test
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { isHttpUrlOrEmpty, safeUrl } from '../url.ts'

test('http 和 https 的链接原样留下（去掉首尾空格）', () => {
  assert.equal(safeUrl('https://example.edu.cn/a.pdf'), 'https://example.edu.cn/a.pdf')
  assert.equal(safeUrl('  HTTP://example.edu.cn/x  '), 'HTTP://example.edu.cn/x')
})

test('别的协议、空的、中间有空格的都不要', () => {
  for (const bad of ['javascript:alert(1)', ' JavaScript:alert(1)', 'data:text/html,hi', 'example.edu.cn', 'https://', 'https://a b', '', null, undefined]) {
    assert.equal(safeUrl(bad), null, String(bad))
  }
})

test('表单校验：空的合格，非 http(s) 不合格', () => {
  assert.equal(isHttpUrlOrEmpty(''), true)
  assert.equal(isHttpUrlOrEmpty('   '), true)
  assert.equal(isHttpUrlOrEmpty('https://example.edu.cn'), true)
  assert.equal(isHttpUrlOrEmpty('javascript:alert(1)'), false)
})

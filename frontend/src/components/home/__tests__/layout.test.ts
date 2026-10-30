// 主页自定义里"往上挪 / 往下挪"的测试：npm test
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { defaultLayout, moveInColumn } from '../layout.ts'

const order = defaultLayout().order

test('同一栏里和上一个、下一个对调，别的栏不动', () => {
  assert.deepEqual(moveInColumn(order, 'messages', -1), ['today', 'schedule', 'semester', 'mine', 'open', 'pending', 'messages', 'recommend'])
  assert.deepEqual(moveInColumn(order, 'mine', 1), ['today', 'schedule', 'semester', 'open', 'mine', 'pending', 'recommend', 'messages'])
})

test('跨过别的栏找同一栏的邻居', () => {
  // 右栏的 pending 往上没有同栏的面板了，不动；中栏的 open 往上是 mine
  assert.deepEqual(moveInColumn(order, 'pending', -1), order)
  const moved = moveInColumn(['open', 'today', 'mine'], 'mine', -1)
  assert.deepEqual(moved, ['mine', 'today', 'open'])
})

test('已经在最前、最后的不动，顶部只有一个面板也不动', () => {
  assert.deepEqual(moveInColumn(order, 'schedule', -1), order)
  assert.deepEqual(moveInColumn(order, 'messages', 1), order)
  assert.deepEqual(moveInColumn(order, 'today', 1), order)
})

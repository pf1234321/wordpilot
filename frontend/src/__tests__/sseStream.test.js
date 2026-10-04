import { describe, it, expect, vi } from 'vitest'
import { mockSseStream } from '../mock/sse'

// 关键回归：sseStream_appendsIncrementally_notFullReplace
// 课件原文：SSE 增量追加（content.value += e.data），不整段替换
describe('SSE 流式增量渲染', () => {
  it('分片推送时内容逐步累积（增量追加）', async () => {
    const full = '这是由多个分片拼成的完整文稿内容'
    const received = []
    let done = false

    mockSseStream(full, {
      chunkSize: 6,
      intervalMs: 1,
      onChunk: (chunk) => received.push(chunk),
      onDone: () => {
        done = true
      }
    })

    // 等待所有分片推完
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(received.length).toBeGreaterThan(1) // 至少 2 片，证明是增量而非一次性
    expect(received.join('')).toBe(full) // 拼接后等于完整内容
    expect(done).toBe(true)
  })

  it('不整段替换：每次只追加当前片，不清空已有内容', async () => {
    let content = ''
    const chunks = []
    mockSseStream('abcdefghijkl', {
      chunkSize: 3,
      intervalMs: 1,
      onChunk: (chunk) => {
        // 模拟 content.value += chunk（增量追加）
        content += chunk
        chunks.push(chunk)
      }
    })
    await new Promise((resolve) => setTimeout(resolve, 40))

    // 每片依次是 ab/cd/ef/gh/ij/kl，长度逐步增长（追加非替换）
    expect(chunks).toEqual(['abc', 'def', 'ghi', 'jkl'])
    expect(content).toBe('abcdefghijkl')
  })

  it('断线时调用 stop 关闭（模拟 es.close() 可重连）', async () => {
    const onError = vi.fn()
    const stop = mockSseStream('hello', {
      chunkSize: 2,
      intervalMs: 1000,
      onChunk: () => {},
      onError
    })
    stop()
    expect(onError).toHaveBeenCalledTimes(1)
  })
})

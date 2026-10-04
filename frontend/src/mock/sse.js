// mock SSE：用定时分片模拟真实 EventSource 的增量推送
// 每片几个字、共十几段，验证前端"增量追加、不整段替换"的渲染逻辑（课件主角二）

// 将完整内容切成若干小片（每片 chunkSize 个字），按 intervalMs 间隔逐片回调
export function mockSseStream(content, { chunkSize = 6, intervalMs = 20, onChunk, onDone, onError } = {}) {
  const chunks = []
  for (let i = 0; i < content.length; i += chunkSize) {
    chunks.push(content.slice(i, i + chunkSize))
  }
  if (chunks.length === 0) {
    chunks.push('')
  }

  let index = 0
  const timer = setInterval(() => {
    if (index < chunks.length) {
      if (typeof onChunk === 'function') {
        onChunk(chunks[index])
      }
      index += 1
    } else {
      clearInterval(timer)
      if (typeof onDone === 'function') {
        onDone()
      }
    }
  }, intervalMs)

  // 返回 stop 函数，模拟断线关闭（es.close()）
  return function stop() {
    clearInterval(timer)
    if (typeof onError === 'function') {
      onError(new Error('mock stream closed'))
    }
  }
}

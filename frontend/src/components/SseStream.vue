<template>
  <div class="sse-stream">
    <pre class="sse-content">{{ content }}</pre>
    <div v-if="streaming" class="sse-status">生成中…（SSE 增量渲染）</div>
    <div v-else-if="done" class="sse-status sse-done">生成完成</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { mockSseStream } from '../mock/sse'

// SSE 流式渲染（课件主角二）：
// 增量追加 content.value += e.data，不整段替换；断线关闭可重连
const props = defineProps({
  url: { type: String, default: '' }
})

const emit = defineEmits(['done'])

const content = ref('')
const streaming = ref(false)
const done = ref(false)

let stopStream = null

// 增量追加，不整段替换
function appendChunk(chunk) {
  content.value += chunk
}

// 增量追加，不整段替换（startStream 内部使用）
function startStream(payload = {}, { chunkSize, intervalMs } = {}) {
  // mock 支线：直接用 mockSseStream 分片推送，模拟真实 SSE
  const source = props.url || 'mock://stream'
  streaming.value = true
  done.value = false
  stopStream = mockSseStream(payload.content || '（空）', {
    chunkSize,
    intervalMs,
    onChunk: appendChunk,
    onDone: () => {
      streaming.value = false
      done.value = true
      emit('done')
    }
  })
  return { source }
}

// 断线关闭（对应 es.close()），可重连
function closeStream() {
  if (stopStream) {
    stopStream()
    stopStream = null
  }
  streaming.value = false
}

defineExpose({ startStream, closeStream })
</script>

<style scoped>
.sse-stream {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  min-height: 120px;
  background: #f8f9fb;
}
.sse-content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  line-height: 1.7;
}
.sse-status {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.sse-done {
  color: #67c23a;
}
</style>

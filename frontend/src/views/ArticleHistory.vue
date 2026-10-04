<template>
  <div class="manage">
    <el-table :data="articles" border stripe class="table">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="mode" label="模式" width="120">
        <template #default="{ row }">
          <el-tag :type="modeTag(row.mode)">{{ modeLabel(row.mode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">查看</el-button>
          <el-button link type="primary" @click="reEdit(row)">重新编辑</el-button>
          <el-button link type="primary" @click="exportWord(row)">导出</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="viewVisible" :title="current?.title || '稿件'" width="60%">
      <pre class="preview-body">{{ current?.content }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mockListArticles, mockDeleteArticle } from '../mock/mockApi'

const emit = defineEmits(['re-edit'])

const articles = ref([])
const viewVisible = ref(false)
const current = ref(null)

onMounted(load)

async function load() {
  articles.value = await mockListArticles()
}

function modeLabel(mode) {
  return { dialog: '一句话写作', rag: '素材仿写', template: '模板写作' }[mode] || mode
}
function modeTag(mode) {
  return { dialog: 'primary', rag: 'success', template: 'warning' }[mode] || 'info'
}

function view(row) {
  current.value = row
  viewVisible.value = true
}

function reEdit(row) {
  emit('re-edit', row)
  ElMessage.success('已回填到对应工作台 Tab')
}

// 导出：mock 模式下载 .txt 文本（真实后端联调时改为导出 Word /api/article/{id}/export）
function exportWord(row) {
  const blob = new Blob([row.content || ''], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.title || '稿件'}.txt`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出')
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除稿件「${row.title}」？`, '提示', { type: 'warning' })
  await mockDeleteArticle(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.manage {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}
.preview-body {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow: auto;
}
</style>

<template>
  <div class="manage">
    <el-upload
      class="upload"
      drag
      :auto-upload="false"
      :limit="1"
      :on-change="onFileChange"
      accept=".txt,.md,.docx,.pdf"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em>素材</div>
      <template #tip>
        <div class="el-upload__tip">仅当前用户可见。支持 txt/md/docx/pdf，上传后自动切片向量化</div>
      </template>
    </el-upload>

    <el-button type="primary" class="upload-btn" :disabled="!pendingFile" @click="doUpload">
      上传 {{ pendingFile?.name || '' }}
    </el-button>

    <el-table :data="materials" border stripe class="table">
      <el-table-column prop="fileName" label="素材名" min-width="180" />
      <el-table-column prop="fileType" label="类型" width="90" />
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ sizeLabel(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="切片数" width="90" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row)">预览</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="previewVisible" title="素材预览" width="60%">
      <pre class="preview-body">{{ previewContent }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { listMaterials, uploadMaterial, previewMaterial, deleteMaterial } from '../api/writingApi'

const materials = ref([])
const pendingFile = ref(null)
const previewVisible = ref(false)
const previewContent = ref('')

onMounted(load)

async function load() {
  materials.value = await listMaterials()
}

function onFileChange(file) {
  pendingFile.value = file
}

function sizeLabel(bytes) {
  if (!bytes && bytes !== 0) return '-'
  return `${(bytes / 1024).toFixed(1)} KB`
}

async function doUpload() {
  if (!pendingFile.value) return
  await uploadMaterial(pendingFile.value.raw)
  pendingFile.value = null
  ElMessage.success('上传成功')
  load()
}

async function preview(row) {
  const m = await previewMaterial(row.id)
  previewContent.value = m?.contentText || '（无内容）'
  previewVisible.value = true
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除素材「${row.fileName}」？`, '提示', { type: 'warning' })
  await deleteMaterial(row.id)
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
.upload-btn {
  align-self: flex-start;
}
.table {
  margin-top: 4px;
}
.preview-body {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow: auto;
}
</style>

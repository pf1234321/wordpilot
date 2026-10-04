<template>
  <el-container class="workbench">
    <el-header class="wb-header">
      <div class="wb-brand">ScriptAgent 智稿工作台</div>
      <div class="wb-user">
        <span>{{ nickname }}</span>
        <el-button link @click="handleLogout">退出</el-button>
      </div>
    </el-header>

    <el-main class="wb-main">
      <el-tabs v-model="activeTab" type="border-card" class="wb-tabs">
        <!-- Tab1 一句话写作 -->
        <el-tab-pane label="一句话写作" name="dialog">
          <div class="tab-body">
            <el-input
              v-model="dialogInput"
              type="textarea"
              :rows="3"
              placeholder="输入一句话，AI 帮你展开成文"
            />
            <el-button type="primary" class="gen-btn" :loading="dialogStreaming" @click="runDialog">
              开始生成
            </el-button>
            <pre v-if="dialogContent" class="sse-output">{{ dialogContent }}</pre>
          </div>
        </el-tab-pane>

        <!-- Tab2 素材仿写 -->
        <el-tab-pane label="素材仿写" name="rag">
          <div class="tab-body">
            <el-select v-model="ragMaterialId" placeholder="选择参考素材（RAG 自动检索）" class="full">
              <el-option
                v-for="m in materials"
                :key="m.id"
                :label="m.fileName"
                :value="m.id"
              />
            </el-select>
            <el-input
              v-model="ragInput"
              type="textarea"
              :rows="3"
              placeholder="输入仿写需求"
            />
            <el-button type="primary" class="gen-btn" :loading="ragStreaming" @click="runRag">
              开始仿写
            </el-button>
            <pre v-if="ragContent" class="sse-output">{{ ragContent }}</pre>
          </div>
        </el-tab-pane>

        <!-- Tab3 模板写作 -->
        <el-tab-pane label="模板写作" name="template">
          <div class="tab-body">
            <el-select v-model="templateId" placeholder="选择模板" class="full" @change="loadTemplateVars">
              <el-option
                v-for="t in templates"
                :key="t.id"
                :label="t.name"
                :value="t.id"
                :disabled="t.status !== 'enabled'"
              />
            </el-select>

            <el-form v-if="currentTemplate" label-width="100px" class="tpl-form">
              <el-form-item
                v-for="v in currentTemplate.variables"
                :key="v.key"
                :label="v.label || v.key"
              >
                <el-input v-model="templateValues[v.key]" :placeholder="v.placeholder || v.key" />
              </el-form-item>
            </el-form>

            <el-button type="primary" class="gen-btn" :loading="templateStreaming" @click="runTemplate">
              开始生成
            </el-button>
            <pre v-if="templateContent" class="sse-output">{{ templateContent }}</pre>
          </div>
        </el-tab-pane>

        <!-- 管理页签 -->
        <el-tab-pane label="素材管理" name="materialManage">
          <MaterialManage />
        </el-tab-pane>
        <el-tab-pane label="模板管理" name="templateManage">
          <TemplateManage />
        </el-tab-pane>
        <el-tab-pane label="稿件历史" name="articleHistory">
          <ArticleHistory @re-edit="reEditArticle" />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { useWorkbenchStore } from '../stores/workbench'
import MaterialManage from './MaterialManage.vue'
import TemplateManage from './TemplateManage.vue'
import ArticleHistory from './ArticleHistory.vue'
import { listMaterials, listTemplates, generate } from '../api/writingApi'

const router = useRouter()
const userStore = useUserStore()
const wb = useWorkbenchStore()

const activeTab = computed({
  get: () => wb.activeTab,
  set: (v) => (wb.activeTab = v)
})

const nickname = computed(() => userStore.nickname || '用户')

const dialogInput = computed({
  get: () => wb.dialogInput,
  set: (v) => (wb.dialogInput = v)
})
const dialogContent = computed(() => wb.dialogContent)
const dialogStreaming = computed(() => wb.dialogStreaming)

const ragMaterialId = computed({
  get: () => wb.ragMaterialId,
  set: (v) => (wb.ragMaterialId = v)
})
const ragInput = computed({
  get: () => wb.ragInput,
  set: (v) => (wb.ragInput = v)
})
const ragContent = computed(() => wb.ragContent)
const ragStreaming = computed(() => wb.ragStreaming)

const templateId = computed({
  get: () => wb.templateId,
  set: (v) => (wb.templateId = v)
})
const templateValues = computed(() => wb.templateValues)
const templateContent = computed(() => wb.templateContent)
const templateStreaming = computed(() => wb.templateStreaming)

const materials = ref([])
const templates = ref([])
const currentTemplate = computed(() => templates.value.find((t) => t.id === wb.templateId) || null)

onMounted(async () => {
  userStore.restore()
  try {
    materials.value = await listMaterials()
  } catch (e) {
    ElMessage.error('素材加载失败')
  }
  try {
    templates.value = await listTemplates()
  } catch (e) {
    ElMessage.error('模板加载失败')
  }
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

function loadTemplateVars(id) {
  const t = templates.value.find((x) => x.id === id)
  if (t) {
    wb.templateValues = {}
    t.variables.forEach((v) => (wb.templateValues[v.key] = ''))
  }
}

function runDialog() {
  if (!dialogInput.value.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  wb.resetTab('dialog')
  wb.dialogStreaming = true
  const { stream } = generate('dialog', { content: dialogInput.value })
  stream(
    (chunk) => (wb.dialogContent += chunk),
    () => (wb.dialogStreaming = false),
    () => (wb.dialogStreaming = false)
  )
}

function runRag() {
  if (!ragInput.value.trim()) {
    ElMessage.warning('请输入仿写需求')
    return
  }
  wb.resetTab('rag')
  wb.ragStreaming = true
  const { stream } = generate('rag', { requirement: ragInput.value })
  stream(
    (chunk) => (wb.ragContent += chunk),
    () => (wb.ragStreaming = false),
    () => (wb.ragStreaming = false)
  )
}

function runTemplate() {
  const t = currentTemplate.value
  if (!t) {
    ElMessage.warning('请选择模板')
    return
  }
  const params = {}
  t.variables.forEach((v) => (params[v.key] = wb.templateValues[v.key] || ''))
  wb.resetTab('template')
  wb.templateStreaming = true
  const { stream } = generate('template', { templateId: t.id, params })
  stream(
    (chunk) => (wb.templateContent += chunk),
    () => (wb.templateStreaming = false),
    () => (wb.templateStreaming = false)
  )
}

// 稿件历史"重新编辑"：回填对应 Tab 并切换过去
function reEditArticle(article) {
  if (article.writeType === 'dialog') {
    wb.dialogInput = article.title
    wb.activeTab = 'dialog'
  } else if (article.writeType === 'rag') {
    wb.ragInput = article.title
    wb.activeTab = 'rag'
  } else {
    wb.activeTab = 'template'
  }
}
</script>

<style scoped>
.workbench {
  height: 100%;
}
.wb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #1f2d3d;
  color: #fff;
}
.wb-brand {
  font-size: 16px;
  font-weight: 600;
}
.wb-user {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cfd8e3;
}
.wb-main {
  background: #f5f7fa;
}
.wb-tabs {
  min-height: 500px;
}
.tab-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}
.full {
  width: 100%;
}
.gen-btn {
  align-self: flex-start;
}
.tpl-form {
  margin-top: 4px;
}
.sse-output {
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  min-height: 120px;
  background: #f8f9fb;
  margin: 0;
  line-height: 1.7;
}
</style>

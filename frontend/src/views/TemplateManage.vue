<template>
  <div class="manage">
    <el-button type="primary" class="add-btn" @click="openCreate">新增模板</el-button>

    <el-table :data="templates" border stripe class="table">
      <el-table-column prop="name" label="模板名" min-width="160" />
      <el-table-column label="变量配置" min-width="200">
        <template #default="{ row }">
          <el-tag v-for="v in row.variables" :key="v.key" class="var-tag">{{ v.key }}</el-tag>
          <span v-if="!row.variables?.length">（无变量）</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" @change="toggle(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑模板' : '新增模板'" width="60%">
      <el-form label-width="100px">
        <el-form-item label="模板名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="变量配置">
          <div class="vars">
            <div v-for="(v, i) in form.variables" :key="i" class="var-row">
              <el-input v-model="v.key" placeholder="变量名" class="var-input" />
              <el-input v-model="v.label" placeholder="标签" class="var-input" />
              <el-input v-model="v.hint" placeholder="提示" class="var-input" />
              <el-button link type="danger" @click="form.variables.splice(i, 1)">移除</el-button>
            </div>
            <el-button size="small" @click="form.variables.push({ key: '', label: '', hint: '' })">
              + 添加变量
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  mockListTemplates,
  mockCreateTemplate,
  mockUpdateTemplate,
  mockDeleteTemplate
} from '../mock/mockApi'

const templates = ref([])
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', variables: [] })

onMounted(load)

async function load() {
  templates.value = await mockListTemplates()
}

function openCreate() {
  editing.value = false
  editingId.value = null
  form.name = ''
  form.variables = []
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  editingId.value = row.id
  form.name = row.name
  form.variables = row.variables.map((v) => ({ ...v }))
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入模板名')
    return
  }
  const variables = form.variables.filter((v) => v.key && v.key.trim())
  if (editing.value) {
    await mockUpdateTemplate(editingId.value, { name: form.name, variables })
    ElMessage.success('已保存')
  } else {
    await mockCreateTemplate({ name: form.name, variables })
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  load()
}

async function toggle(row) {
  await mockUpdateTemplate(row.id, { enabled: row.enabled })
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除模板「${row.name}」？`, '提示', { type: 'warning' })
  await mockDeleteTemplate(row.id)
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
.add-btn {
  align-self: flex-start;
}
.var-tag {
  margin-right: 6px;
}
.vars {
  width: 100%;
}
.var-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.var-input {
  width: 150px;
}
</style>

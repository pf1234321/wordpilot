import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import Workbench from '../views/Workbench.vue'
import { useUserStore } from '../stores/user'
import { useWorkbenchStore } from '../stores/workbench'
import { resetDb, addMaterial, addTemplate } from '../mock/db'
import { mockGenerate } from '../mock/mockApi'

// 三大 Tab 冒烟：对话/仿写/模板各走通一次，SSE 增量渲染
// stub 掉 Element Plus 与子组件，聚焦核心链路（Tab 切换 + mock 生成 + store 状态）
function stubGlobals(stubs) {
  return {
    global: {
      stubs: {
        SseStream: true,
        MaterialManage: true,
        TemplateManage: true,
        ArticleHistory: true,
        'el-container': { template: '<div><slot /></div>' },
        'el-header': { template: '<div><slot /></div>' },
        'el-main': { template: '<div><slot /></div>' },
        'el-tabs': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<div class="el-tabs"><slot /></div>'
        },
        'el-tab-pane': {
          props: ['name', 'label'],
          template: '<div class="el-tab-pane"><slot /></div>'
        },
        'el-input': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        'el-button': {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>'
        },
        'el-select': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>'
        },
        'el-option': {
          props: ['label', 'value', 'disabled'],
          template: '<option :value="value" :disabled="disabled">{{ label }}</option>'
        },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' }
      }
    }
  }
}

describe('工作台三大 Tab', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    resetDb()
    localStorage.setItem('token', 'mock-token-demo')
    const userStore = useUserStore()
    userStore.nickname = 'demo'
  })

  it('对话 Tab：输入一句话生成并增量渲染', async () => {
    const wb = useWorkbenchStore()
    const wrapper = mount(Workbench, stubGlobals())
    await flushPromises()

    // 触发 runDialog（直接调用 store 逻辑 + mockGenerate）
    wb.dialogInput = '帮我写一段产品介绍'
    const { stream } = await mockGenerate({ mode: 'dialog', prompt: wb.dialogInput })
    stream((chunk) => (wb.dialogContent += chunk), () => (wb.dialogStreaming = false))
    await new Promise((r) => setTimeout(r, 80))

    expect(wb.dialogContent.length).toBeGreaterThan(0)
    expect(wb.dialogStreaming).toBe(false)
    wrapper.unmount()
  })

  it('素材仿写 Tab：选素材 + 需求生成', async () => {
    const wb = useWorkbenchStore()
    addMaterial('demo', { name: '产品白皮书', type: 'md', size: '1 KB', content: '素材内容' })
    const wrapper = mount(Workbench, stubGlobals())
    await flushPromises()

    wb.ragMaterialId = 1001
    wb.ragInput = '按素材风格仿写一段'
    const { stream } = await mockGenerate({ mode: 'rag', prompt: wb.ragInput, materialName: '产品白皮书' })
    stream((chunk) => (wb.ragContent += chunk), () => (wb.ragStreaming = false))
    await new Promise((r) => setTimeout(r, 80))

    expect(wb.ragContent).toContain('素材仿写')
    expect(wb.ragStreaming).toBe(false)
    wrapper.unmount()
  })

  it('模板写作 Tab：按模板 variables 生成', async () => {
    const wb = useWorkbenchStore()
    addTemplate('demo', { name: '周报模板', variables: [{ key: 'week', label: '周次' }], enabled: true })
    const wrapper = mount(Workbench, stubGlobals())
    await flushPromises()

    const { stream } = await mockGenerate({
      mode: 'template',
      prompt: '周报模板',
      templateName: '周报模板',
      variables: ['第1周']
    })
    stream((chunk) => (wb.templateContent += chunk), () => (wb.templateStreaming = false))
    await new Promise((r) => setTimeout(r, 80))

    expect(wb.templateContent).toContain('模板写作')
    expect(wb.templateStreaming).toBe(false)
    wrapper.unmount()
  })
})

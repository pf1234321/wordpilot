import { defineStore } from 'pinia'
import { ref } from 'vue'

// 工作台状态：三大 Tab 统一管理，避免各组件自存状态混乱（课件坑）
export const useWorkbenchStore = defineStore('workbench', () => {
  const activeTab = ref('dialog')

  // 一句话写作（dialog）
  const dialogInput = ref('')
  const dialogContent = ref('')
  const dialogStreaming = ref(false)

  // 素材仿写（rag）
  const ragMaterialId = ref(null)
  const ragInput = ref('')
  const ragContent = ref('')
  const ragStreaming = ref(false)

  // 模板写作（template）
  const templateId = ref(null)
  const templateValues = ref({})
  const templateContent = ref('')
  const templateStreaming = ref(false)

  function resetTab(tab) {
    if (tab === 'dialog') {
      dialogContent.value = ''
      dialogStreaming.value = false
    } else if (tab === 'rag') {
      ragContent.value = ''
      ragStreaming.value = false
    } else if (tab === 'template') {
      templateContent.value = ''
      templateStreaming.value = false
    }
  }

  return {
    activeTab,
    dialogInput,
    dialogContent,
    dialogStreaming,
    ragMaterialId,
    ragInput,
    ragContent,
    ragStreaming,
    templateId,
    templateValues,
    templateContent,
    templateStreaming,
    resetTab
  }
})

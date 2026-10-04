<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">ScriptAgent 智稿工作台</div>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
        <div v-if="error" class="login-error">{{ error }}</div>
        <div class="login-hint">全链路模式：账号密码经真实后端 /api/auth/login 校验</div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  error.value = ''
  await formRef.value.validate().catch(() => {
    throw new Error('校验失败')
  })
  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    router.push('/workbench')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a5a80 100%);
}
.login-card {
  width: 380px;
}
.login-title {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}
.login-btn {
  width: 100%;
}
.login-error {
  color: #f56c6c;
  font-size: 13px;
  margin-bottom: 8px;
  text-align: center;
}
.login-hint {
  color: #909399;
  font-size: 12px;
  text-align: center;
}
</style>

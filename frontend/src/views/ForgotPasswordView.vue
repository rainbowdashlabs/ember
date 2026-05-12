/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {auth} from '@/api'

const {t} = useI18n()

const email = ref('')
const loading = ref(false)
const error = ref('')
const success = ref('')

async function handleReset() {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    await auth.forgotPassword({email: email.value})
    success.value = t('forgotPassword.sent')
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-sm space-y-6">
      <div class="text-center">
        <font-awesome-icon :icon="['fas', 'lock']" class="text-4xl text-primary mb-3"/>
        <h1 class="text-2xl font-bold">{{ t('forgotPassword.title') }}</h1>
        <p class="text-sm text-(--text-muted) mt-2">{{ t('forgotPassword.hint') }}</p>
      </div>

      <div class="space-y-4">
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <Alert v-if="success" variant="success">{{ success }}</Alert>

        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('forgotPassword.email') }}</label>
          <TextInput v-model="email" :placeholder="t('forgotPassword.email')"/>
        </div>

        <PrimaryButton :disabled="loading || !email" class="w-full" @click="handleReset">
          {{ loading ? t('common.loading') : t('forgotPassword.submit') }}
        </PrimaryButton>

        <router-link class="block w-full text-center text-sm text-(--text-muted) hover:text-(--text) transition-colors"
                     to="/login">
          {{ t('forgotPassword.backToLogin') }}
        </router-link>
      </div>
    </div>
  </div>
</template>

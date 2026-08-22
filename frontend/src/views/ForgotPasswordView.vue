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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()

const email = ref('')
const success = ref('')

const {running: loading, error, run: handleReset} = useAsyncAction(async () => {
  success.value = ''
  await auth.forgotPassword({email: email.value})
  success.value = t('forgotPassword.sent')
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-xs space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'lock']"/>
        <PageHeader class="text-2xl font-bold">{{ t('forgotPassword.title') }}</PageHeader>
        <p class="text-sm text-(--text-muted) mt-2">{{ t('forgotPassword.hint') }}</p>
      </div>

      <div class="space-y-4">
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <Alert v-if="success" variant="success">{{ success }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('forgotPassword.email') }}</FieldLabel>
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {auth} from '@/api'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()

const newPassword = ref('')
const confirmPassword = ref('')
const validationError = ref('')

const token = route.query.token as string

const {running: loading, error: submitError, run: runSetPassword} = useAsyncAction(async () => {
  await auth.setPassword({token, password: newPassword.value})
  await router.push({name: 'login'})
}, {formatError: (e) => {
  const raw = (e as {response?: {data?: {message?: string}}})?.response?.data?.message
  return raw ? (te(raw) ? t(raw) : raw) : t('common.error')
}})

const error = computed(() => validationError.value || submitError.value)

function handleSetPassword() {
  if (!newPassword.value) {
    validationError.value = t('setPassword.required')
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    validationError.value = t('setPassword.mismatch')
    return
  }

  validationError.value = ''
  void runSetPassword()
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-sm space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'lock']"/>
        <PageHeader class="text-2xl font-bold">{{ t('setPassword.title') }}</PageHeader>
      </div>

      <form class="space-y-4" @submit.prevent="handleSetPassword">
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('setPassword.newPassword') }}</FieldLabel>
          <PasswordInput
              v-model="newPassword"
              :disabled="loading"
              :placeholder="t('setPassword.newPassword')"
          />
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('setPassword.confirmPassword') }}</FieldLabel>
          <PasswordInput
              v-model="confirmPassword"
              :disabled="loading"
              :placeholder="t('setPassword.confirmPassword')"
          />
        </div>

        <PrimaryButton
            :disabled="loading || !newPassword || !confirmPassword"
            class="w-full"
            @click="handleSetPassword"
        >
          {{ loading ? t('common.loading') : t('setPassword.submit') }}
        </PrimaryButton>
      </form>
    </div>
  </div>
</template>

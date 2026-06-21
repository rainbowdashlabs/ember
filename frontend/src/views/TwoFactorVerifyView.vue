/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {verify2fa} from '@/api/twoFactor'
import {setItem} from '@/api/storage'
import {scheduleTokenRefresh} from '@/api/client'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const route = useRoute()

const preAuthToken = ref(route.query.token as string || '')
const code = ref('')
const error = ref('')
const loading = ref(false)
const useBackupCode = ref(false)

async function handleVerify() {
  if (!code.value || !preAuthToken.value) return
  error.value = ''
  loading.value = true
  try {
    const factor = useBackupCode.value ? 'BACKUP_CODE' : 'TOTP'
    const result = await verify2fa(preAuthToken.value, factor, code.value)
    setItem('session_token', result.token)
    setItem('session_expires_at', result.expiresAt)
    scheduleTokenRefresh(result.expiresAt)
    window.location.href = '/station/requirements'
  } catch {
    error.value = t('twoFactor.verify.invalidCode')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full max-w-sm space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'shield']"/>
        <PageHeader>{{ t('twoFactor.verify.title') }}</PageHeader>
        <MutedText tag="p" size="sm" class="mt-1">
          {{ useBackupCode ? t('twoFactor.verify.backupHint') : t('twoFactor.verify.totpHint') }}
        </MutedText>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="space-y-4">
        <form class="space-y-4" @submit.prevent="handleVerify">
          <TextInput
              v-model="code"
              :placeholder="useBackupCode ? 'XXXX-XXXX-XXXX' : '000000'"
              :disabled="loading"
              autocomplete="one-time-code"
              inputmode="numeric"
          />
          <PrimaryButton :disabled="loading || !code" class="w-full" @click="handleVerify">
            {{ loading ? t('common.loading') : t('twoFactor.verify.submit') }}
          </PrimaryButton>
        </form>

        <div class="text-center">
          <LinkButton @click="useBackupCode = !useBackupCode">
            {{ useBackupCode ? t('twoFactor.verify.useAuthenticator') : t('twoFactor.verify.useBackupCode') }}
          </LinkButton>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>

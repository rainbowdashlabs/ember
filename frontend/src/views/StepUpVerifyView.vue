/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {stepUp} from '@/api/twoFactor'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

type Category = 'ACCOUNT_SECURITY' | 'FEDERATION' | 'INSTANCE_CONFIG' | 'ROLE_CHANGE'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const code = ref('')
const useBackupCode = ref(false)
const loading = ref(false)
const error = ref('')

const category = computed<Category | null>(() => {
  const c = route.query.category as string | undefined
  if (c === 'ACCOUNT_SECURITY' || c === 'FEDERATION' || c === 'INSTANCE_CONFIG' || c === 'ROLE_CHANGE') return c
  return null
})

const redirectTarget = computed(() => {
  const r = route.query.redirect as string | undefined
  if (!r || !r.startsWith('/')) return '/station/requirements'
  return r
})

const categoryHint = computed(() => {
  switch (category.value) {
    case 'ACCOUNT_SECURITY': return t('twoFactor.stepUp.categoryAccountSecurity')
    case 'FEDERATION': return t('twoFactor.stepUp.categoryFederation')
    case 'INSTANCE_CONFIG': return t('twoFactor.stepUp.categoryInstanceConfig')
    case 'ROLE_CHANGE': return t('twoFactor.stepUp.categoryRoleChange')
    default: return t('twoFactor.stepUp.description')
  }
})

async function handleVerify() {
  if (!code.value) return
  loading.value = true
  error.value = ''
  try {
    const factor: string = useBackupCode.value ? 'BACKUP_CODE' : 'TOTP'
    await stepUp(factor, code.value)
    await router.replace(redirectTarget.value)
  } catch {
    error.value = t('twoFactor.stepUp.invalidCode')
  } finally {
    loading.value = false
  }
}

function handleCancel() {
  router.replace(redirectTarget.value)
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full max-w-sm space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'shield']"/>
        <PageHeader>{{ t('twoFactor.stepUp.title') }}</PageHeader>
        <MutedText tag="p" size="sm" class="mt-1">{{ categoryHint }}</MutedText>
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
          <div class="flex justify-between gap-2">
            <SecondaryButton type="button" :disabled="loading" @click="handleCancel">
              {{ t('twoFactor.stepUp.cancel') }}
            </SecondaryButton>
            <PrimaryButton :disabled="loading || !code" type="submit">
              {{ loading ? t('common.loading') : t('twoFactor.stepUp.submit') }}
            </PrimaryButton>
          </div>
        </form>

        <div class="text-center">
          <LinkButton type="button" @click="useBackupCode = !useBackupCode">
            {{ useBackupCode ? t('twoFactor.verify.useAuthenticator') : t('twoFactor.verify.useBackupCode') }}
          </LinkButton>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>

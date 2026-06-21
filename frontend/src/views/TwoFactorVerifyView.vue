/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {verify2fa, webauthnLoginBegin, webauthnLoginFinish} from '@/api/twoFactor'
import {getWebAuthnCredential, isWebAuthnSupported} from '@/util/webauthn'
import {setItem} from '@/api/storage'
import {scheduleTokenRefresh} from '@/api/client'
import {session} from '@/api'
import {useStations} from '@/composables/useStations'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const route = useRoute()
const {setActiveStation} = useStations()

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
    finalizeSession(result.token, result.expiresAt)
  } catch {
    error.value = t('twoFactor.verify.invalidCode')
  } finally {
    loading.value = false
  }
}

const webauthnSupported = isWebAuthnSupported()

async function handleWebAuthn() {
  if (!preAuthToken.value) return
  error.value = ''
  loading.value = true
  try {
    const begin = await webauthnLoginBegin(preAuthToken.value)
    const credentialJson = await getWebAuthnCredential(begin.optionsJson)
    const result = await webauthnLoginFinish(preAuthToken.value, begin.challengeToken, credentialJson)
    finalizeSession(result.token, result.expiresAt)
  } catch (e: any) {
    if (e?.message === 'webauthn-cancelled') {
      error.value = t('twoFactor.webauthn.cancelled')
    } else if (e?.message === 'webauthn-unsupported') {
      error.value = t('twoFactor.webauthn.unsupported')
    } else {
      error.value = t('twoFactor.verify.invalidCode')
    }
  } finally {
    loading.value = false
  }
}

async function finalizeSession(token: string, expiresAt: string) {
  setItem('session_token', token)
  setItem('session_expires_at', expiresAt)
  scheduleTokenRefresh(expiresAt)
  // Resolve the user's station the same way the regular login flow does. Without this the
  // browser holds a session token but no X-Station-Id header, so every authenticated
  // request resolves to an empty StationPermission set and the dashboard 403s on USER.
  try {
    const stations = await session.getStations()
    if (stations.length === 1) {
      setActiveStation(stations[0].stationId)
      window.location.href = '/station/requirements'
      return
    }
    if (stations.length > 1) {
      window.location.href = '/cross-station'
      return
    }
  } catch { /* fall through to default redirect */ }
  window.location.href = '/station/requirements'
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

        <SecondaryButton v-if="webauthnSupported" type="button" :disabled="loading" class="w-full" @click="handleWebAuthn">
          <font-awesome-icon :icon="['fas', 'key']" class="mr-1"/>
          {{ t('twoFactor.webauthn.useKey') }}
        </SecondaryButton>

        <div class="text-center">
          <LinkButton @click="useBackupCode = !useBackupCode">
            {{ useBackupCode ? t('twoFactor.verify.useAuthenticator') : t('twoFactor.verify.useBackupCode') }}
          </LinkButton>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>

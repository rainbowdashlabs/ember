/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {getTwoFactorStatus, verify2fa, webauthnLoginBegin, webauthnLoginFinish} from '@/api/twoFactor'
import {getWebAuthnCredential, isWebAuthnSupported} from '@/util/webauthn'
import {setItem} from '@/api/storage'
import {scheduleTokenRefresh} from '@/api/client'
import {session} from '@/api'
import {useStations} from '@/composables/useStations'
import {useAsyncAction} from '@/composables/useAsyncAction'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
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
const {setActiveStation, clearActiveStation} = useStations()

const preAuthToken = ref(route.query.token as string || '')
/** Carried over from the login screen, so ticking the box there is not undone by the second factor. */
const trustedDevice = ref(route.query.trusted === '1')
const code = ref('')
const useBackupCode = ref(false)

const rememberDevice = ref(false)
const trustedDeviceMaxDays = ref(0)

// The pre-auth token doesn't grant access to /account/2fa/status, but the value only
// affects an unauthenticated view of the trusted-device window. Fall back to a
// default of 30 when the call fails so the checkbox still shows a sensible cap.
onMounted(async () => {
  try {
    const status = await getTwoFactorStatus()
    trustedDeviceMaxDays.value = status.trustedDeviceMaxDays
  } catch {
    trustedDeviceMaxDays.value = 30
  }
})

const {running: verifying, error: verifyError, run: runVerify, clearError: clearVerifyError} = useAsyncAction(async () => {
  if (!code.value || !preAuthToken.value) return
  const factor = useBackupCode.value ? 'BACKUP_CODE' : 'TOTP'
  const days = rememberDevice.value ? trustedDeviceMaxDays.value : undefined
  const result = await verify2fa(preAuthToken.value, factor, code.value, days, trustedDevice.value)
  finalizeSession(result.token, result.expiresAt)
}, {formatError: () => t('twoFactor.verify.invalidCode')})

const webauthnSupported = isWebAuthnSupported()

const {running: webauthnRunning, error: webauthnError, run: runWebAuthn, clearError: clearWebauthnError} = useAsyncAction(async () => {
  if (!preAuthToken.value) return
  const begin = await webauthnLoginBegin(preAuthToken.value)
  const credentialJson = await getWebAuthnCredential(begin.optionsJson)
  const days = rememberDevice.value ? trustedDeviceMaxDays.value : undefined
  const result = await webauthnLoginFinish(preAuthToken.value, begin.challengeToken, credentialJson, days)
  finalizeSession(result.token, result.expiresAt)
}, {formatError: (e) => {
  const message = (e as Error | undefined)?.message
  if (message === 'webauthn-cancelled') return t('twoFactor.webauthn.cancelled')
  if (message === 'webauthn-unsupported') return t('twoFactor.webauthn.unsupported')
  return t('twoFactor.verify.invalidCode')
}})

const loading = computed(() => verifying.value || webauthnRunning.value)
const error = computed(() => verifyError.value || webauthnError.value)

function handleVerify() {
  clearWebauthnError()
  void runVerify()
}

function handleWebAuthn() {
  clearVerifyError()
  void runWebAuthn()
}

async function finalizeSession(token: string, expiresAt: string) {
  setItem('session_token', token)
  setItem('session_expires_at', expiresAt)
  clearActiveStation()
  scheduleTokenRefresh(expiresAt)
  const redirect = route.query.redirect as string | undefined
  try {
    const [stations, info] = await Promise.all([
      session.getStations(),
      session.getSessionInfo().catch(() => null),
    ])
    const isAdmin = info?.instanceUserType === 'ADMINISTRATOR'
    const [onlyStation] = stations
    if (stations.length === 1 && onlyStation) {
      setActiveStation(onlyStation.stationId)
      window.location.href = redirect || '/station/requirements'
      return
    }
    if (stations.length > 1) {
      window.location.href = redirect || '/cross-station'
      return
    }
    if (isAdmin) {
      window.location.href = redirect || '/admin/dashboard/overview'
      return
    }
    window.location.href = redirect || '/account'
    return
  } catch { /* fall through to default redirect */ }
  window.location.href = redirect || '/station/requirements'
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full max-w-xs space-y-6">
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
          <label v-if="trustedDeviceMaxDays > 0" class="flex items-center gap-2 text-sm">
            <CheckboxInput v-model="rememberDevice" :disabled="loading"/>
            <span>{{ t('twoFactor.verify.rememberDevice', {n: trustedDeviceMaxDays}) }}</span>
          </label>
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

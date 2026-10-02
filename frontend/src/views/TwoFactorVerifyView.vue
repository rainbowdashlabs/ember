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
import {hasSessionCookie} from '@/api/sessionCookie'
import {decideSignInLanding} from '@/util/signInLanding'
import {describeFailure} from '@/util/failure'
import {useCluster} from '@/composables/useCluster'
import {useStations} from '@/composables/useStations'
import {useAsyncAction} from '@/composables/useAsyncAction'
import TextInput from '@/components/input/text/TextInput.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

const preAuthToken = ref(route.query.token as string || '')
/** Carried over from the login screen, so ticking the box there is not undone by the second factor. */
const trustedDevice = ref(route.query.trusted === '1')
const code = ref('')
const useBackupCode = ref(false)

const rememberDevice = ref(false)
/**
 * How long a device may be trusted. The pre-auth token cannot read the second-factor status, so a
 * failed read falls back to 30 days and the checkbox still shows a sensible cap.
 */
const trustedDeviceMaxDays = ref(0)

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
  await verify2fa(preAuthToken.value, factor, code.value, days, trustedDevice.value)
  await finalizeSession()
}, {formatError: (e) => describeFailure(e, t).message})

const webauthnSupported = isWebAuthnSupported()

const {running: webauthnRunning, error: webauthnError, run: runWebAuthn, clearError: clearWebauthnError} = useAsyncAction(async () => {
  if (!preAuthToken.value) return
  const begin = await webauthnLoginBegin(preAuthToken.value)
  const credentialJson = await getWebAuthnCredential(begin.optionsJson)
  const days = rememberDevice.value ? trustedDeviceMaxDays.value : undefined
  await webauthnLoginFinish(preAuthToken.value, begin.challengeToken, credentialJson, days, trustedDevice.value)
  await finalizeSession()
}, {formatError: (e) => {
  const message = (e as Error | undefined)?.message
  if (message === 'webauthn-cancelled') return t('twoFactor.webauthn.cancelled')
  if (message === 'webauthn-unsupported') return t('twoFactor.webauthn.unsupported')
  return describeFailure(e, t).message
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

/**
 * Leaves for the signed-in application, unless the session was taken away while this ran.
 *
 * <p>A request answered 401 on this screen clears the session cookie without redirecting, because
 * the two-factor screen counts as a public path. Walking on regardless put the reader on a page
 * that immediately bounced them back to the login screen with nothing said, which reads as the
 * second factor having silently failed. Going there directly, keeping where they were headed, is
 * the honest version of the same outcome.
 */
function leaveFor(path: string) {
  if (!hasSessionCookie()) {
    window.location.href = '/login?redirect=' + encodeURIComponent(path)
    return
  }
  window.location.href = path
}

/**
 * Both contexts are dropped before the session is described, the same way the login screen does
 * it. What the browser remembers belongs to whoever signed in last, and a station or association
 * carried over from them is named on every call this account makes afterwards.
 */
async function finalizeSession() {
  clearActiveStation()
  clearActiveCluster()
  const redirect = route.query.redirect as string | undefined
  try {
    const landing = await decideSignInLanding(redirect)
    if (landing.stationId) setActiveStation(landing.stationId)
    if (landing.clusterUid) setActiveCluster(landing.clusterUid)
    leaveFor(landing.path)
  } catch {
    leaveFor(redirect || '/station/requirements')
  }
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

      <FailureAlert :message="error"/>

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

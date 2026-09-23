/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import DeviceIdentifierForm from './deviceunlockview/DeviceIdentifierForm.vue'
import DeviceHandshakeWaiting from './deviceunlockview/DeviceHandshakeWaiting.vue'
import DeviceHandshakeFailure from './deviceunlockview/DeviceHandshakeFailure.vue'
import {passkeys} from '@/api'
import type {DeviceRequest} from '@/api/passkeys'
import {useBackingOffPoll, type PollOutcome} from '@/composables/useBackingOffPoll'
import {describeFailure, FailureKind} from '@/util/failure'
import {createWebAuthnCredential, getWebAuthnCredential, isWebAuthnSupported, webauthnErrorKey} from '@/util/webauthn'
import {decideSignInLanding} from '@/util/signInLanding'
import {showToast} from '@/util/toast'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'
import {useSession} from '@/composables/useSession'

/**
 * The new device's half of the handshake: it shows a short code, a device that is already
 * signed in approves it, and the poll that follows hands this one a token that creates its
 * passkey. Then it signs in with the passkey it just made, like any other sign-in.
 */
const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()
const session = useSession()

type Phase =
    | 'choosing'
    | 'identifying'
    | 'loading'
    | 'waiting'
    | 'enrolling'
    | 'signingIn'
    | 'done'
    | 'expired'
    | 'rejected'
    | 'failed'
const phase = ref<Phase>('loading')
const error = ref('')
const request = ref<DeviceRequest | null>(null)
const signedInAs = ref('')

/**
 * What this device is asking for. A passkey is the better answer where the browser can hold one: it
 * works again tomorrow without a second device. A session is what is left where it cannot, and what
 * somebody wants on a machine they are borrowing.
 */
const wants = ref<'ENROL_PASSKEY' | 'SIGN_IN'>('ENROL_PASSKEY')

/**
 * Whether a passkey is on offer at all. Two things have to be true, and asking only the first is
 * what made the screen offer a button the server then refused: the browser has to be able to hold
 * one, and the instance has to use them. It starts false so that the choice is never shown before
 * the instance has answered.
 */
const browserHoldsPasskeys = isWebAuthnSupported()
const canHoldPasskey = ref(false)

/**
 * What to put on the screen when something went wrong.
 *
 * <p>Being refused for asking too often is worth saying in those words: the reader can act on it by
 * waiting, where the flow's own sentence tells them to fetch a new code, which spends another
 * request from the bucket that just refused. Everything else keeps the sentence it always had.
 */
function failureText(e: unknown, fallback: string): string {
  const failure = describeFailure(e, t)
  if (failure.kind !== FailureKind.TOO_OFTEN) return fallback
  return `${failure.message} ${failure.guidance}`
}

/** Remembers who is asking, so the retry button does not send the reader back to the field. */
const identifier = ref('')

function chose(want: 'ENROL_PASSKEY' | 'SIGN_IN') {
  wants.value = want
  phase.value = 'identifying'
}

async function start(named: string = identifier.value) {
  stopPolling()
  identifier.value = named
  error.value = ''
  phase.value = 'loading'
  try {
    request.value = wants.value === 'SIGN_IN'
        ? await passkeys.signInRequest(named)
        : await passkeys.deviceRequest(named)
    phase.value = 'waiting'
    startPolling()
  } catch (e) {
    phase.value = 'failed'
    error.value = failureText(e, t('passkeys.device.requestFailed'))
  }
}

/** One ask, saying whether the wait goes on. What it found decides where the screen goes next. */
async function poll(): Promise<PollOutcome> {
  if (!request.value || phase.value !== 'waiting') return 'stop'
  const result = await passkeys.devicePoll(request.value.pollSecret)
  if (result.status === 'APPROVED' && result.enrollToken) {
    if (result.purpose === 'SIGN_IN') await claimSession(result.enrollToken)
    else await enroll(result.enrollToken)
    return 'stop'
  }
  if (result.status === 'REJECTED') {
    phase.value = 'rejected'
    return 'stop'
  }
  if (result.status === 'EXPIRED' || result.status === 'UNKNOWN') {
    phase.value = 'expired'
    return 'stop'
  }
  return 'again'
}

const {start: startPolling, stop: stopPolling, throttled} = useBackingOffPoll(poll)

/**
 * The sign-in half: the approval bought a session, so there is no ceremony to run. Nothing is left
 * on this device, which is the whole reason somebody chooses this path on a machine they borrowed.
 */
async function claimSession(claimToken: string) {
  phase.value = 'signingIn'
  try {
    await passkeys.signInClaim(claimToken)
    await landAfterSignIn()
  } catch (e) {
    phase.value = 'failed'
    error.value = failureText(e, t('passkeys.device.signInFailed'))
  }
}

async function enroll(enrollToken: string) {
  phase.value = 'enrolling'
  try {
    const begin = await passkeys.deviceEnrollBegin(enrollToken)
    const credentialJson = await createWebAuthnCredential(begin.optionsJson)
    await passkeys.deviceEnrollFinish(enrollToken, begin.challengeToken, credentialJson)
    await signInWithNewPasskey()
  } catch (e) {
    phase.value = 'failed'
    error.value = t(webauthnErrorKey(e, 'create'))
  }
}

/** The enrolment made a credential and nothing more; signing in with it is the ordinary path. */
async function signInWithNewPasskey() {
  phase.value = 'signingIn'
  try {
    const begin = await passkeys.passkeySignInBegin()
    const credentialJson = await getWebAuthnCredential(begin.optionsJson)
    await passkeys.passkeySignInFinish(begin.challengeToken, credentialJson, false)
    await landAfterSignIn()
  } catch (e) {
    phase.value = 'failed'
    error.value = t(webauthnErrorKey(e, 'get'))
  }
}

/**
 * Where a fresh session lands, and whose it turned out to be.
 *
 * <p>The name is read back and said out loud deliberately. Somebody whose open code a stranger
 * guessed and approved would otherwise work inside that stranger's account without ever being told.
 * It goes out as a toast rather than onto this screen, because this screen is gone the moment the
 * navigation lands: the warning has to arrive where the reader actually ends up.
 */
async function landAfterSignIn() {
  phase.value = 'done'
  const redirectPath = route.query.redirect as string | undefined
  clearActiveStation()
  clearActiveCluster()
  const landing = await decideSignInLanding(redirectPath)
  await session.load()
  signedInAs.value = session.fullName()
  showToast(t('passkeys.device.signedInAs', {name: signedInAs.value}), 'info', 8000)
  if (landing.stationId) setActiveStation(landing.stationId)
  if (landing.clusterUid) setActiveCluster(landing.clusterUid)
  await navigateTo(landing.path)
}

/**
 * A device with only one way in is not asked to choose. That is a browser that cannot hold a
 * passkey, and equally an instance that does not use them: both leave signing in as the only answer,
 * so the screen goes straight to it rather than offering a button that would be refused.
 */
onMounted(async () => {
  if (browserHoldsPasskeys) {
    try {
      canHoldPasskey.value = (await passkeys.publicPasskeyMode()) !== 'OFF'
    } catch {
      canHoldPasskey.value = false
    }
  }
  if (canHoldPasskey.value) phase.value = 'choosing'
  else chose('SIGN_IN')
})
onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full max-w-md space-y-6 text-center">
      <PageHeroIcon :icon="['fas', 'fingerprint']"/>
      <PageHeader class="text-2xl font-bold">{{ t('passkeys.device.title') }}</PageHeader>

      <template v-if="phase === 'choosing'">
        <p>{{ t('passkeys.device.chooseIntro') }}</p>
        <PrimaryButton class="w-full" :icon="['fas', 'fingerprint']" @click="chose('ENROL_PASSKEY')">
          {{ t('passkeys.device.choosePasskey') }}
        </PrimaryButton>
        <MutedText tag="p" size="sm">{{ t('passkeys.device.choosePasskeyHint') }}</MutedText>
        <SecondaryButton class="w-full" :icon="['fas', 'right-to-bracket']" @click="chose('SIGN_IN')">
          {{ t('passkeys.device.chooseSignIn') }}
        </SecondaryButton>
        <MutedText tag="p" size="sm">{{ t('passkeys.device.chooseSignInHint') }}</MutedText>
      </template>

      <DeviceIdentifierForm v-else-if="phase === 'identifying'" @submit="start"/>

      <Spinner v-else-if="phase === 'loading'" size="lg" class="mx-auto"/>

      <DeviceHandshakeWaiting
          v-else-if="phase === 'waiting' && request"
          :request="request"
          :throttled="throttled"
          :wants-sign-in="wants === 'SIGN_IN'"
      />

      <p v-else-if="phase === 'enrolling'">{{ t('passkeys.create.preparing') }}</p>
      <p v-else-if="phase === 'signingIn'">{{ t('passkeys.device.signingIn') }}</p>
      <p v-else-if="phase === 'done'">
        {{ signedInAs ? t('passkeys.device.signedInAs', {name: signedInAs}) : t('passkeys.device.signingIn') }}
      </p>

      <DeviceHandshakeFailure
          v-else-if="phase === 'rejected'"
          :hint="t('passkeys.device.rejectedHint')"
          :message="t('passkeys.device.rejected')"
          test-id="device-rejected"
          @retry="start()"
      />

      <DeviceHandshakeFailure v-else :message="error || t('passkeys.device.expired')" @retry="start()"/>
    </div>
  </div>
</template>

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
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {passkeys} from '@/api'
import type {DeviceRequest} from '@/api/passkeys'
import {createWebAuthnCredential, getWebAuthnCredential, webauthnErrorKey} from '@/util/webauthn'
import {decideSignInLanding} from '@/util/signInLanding'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'

/**
 * The new device's half of the handshake: it shows a short code, a device that is already
 * signed in approves it, and the poll that follows hands this one a token that creates its
 * passkey. Then it signs in with the passkey it just made, like any other sign-in.
 */
const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

type Phase = 'loading' | 'waiting' | 'enrolling' | 'signingIn' | 'done' | 'expired' | 'failed'
const phase = ref<Phase>('loading')
const error = ref('')
const request = ref<DeviceRequest | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const groupedCode = computed(() => {
  const code = request.value?.code ?? ''
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code
})

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

async function start() {
  stopPolling()
  error.value = ''
  phase.value = 'loading'
  try {
    request.value = await passkeys.deviceRequest()
    phase.value = 'waiting'
    pollTimer = setInterval(poll, 2500)
  } catch {
    phase.value = 'failed'
    error.value = t('passkeys.device.requestFailed')
  }
}

async function poll() {
  if (!request.value || phase.value !== 'waiting') return
  try {
    const result = await passkeys.devicePoll(request.value.pollSecret)
    if (result.status === 'APPROVED' && result.enrollToken) {
      stopPolling()
      await enroll(result.enrollToken)
    } else if (result.status === 'EXPIRED' || result.status === 'UNKNOWN') {
      stopPolling()
      phase.value = 'expired'
    }
  } catch {
    // A lost poll is nothing; the next tick asks again.
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
    phase.value = 'done'
    const redirectPath = route.query.redirect as string | undefined
    clearActiveStation()
    clearActiveCluster()
    const landing = await decideSignInLanding(redirectPath)
    if (landing.stationId) setActiveStation(landing.stationId)
    if (landing.clusterUid) setActiveCluster(landing.clusterUid)
    await navigateTo(landing.path)
  } catch (e) {
    phase.value = 'failed'
    error.value = t(webauthnErrorKey(e, 'get'))
  }
}

onMounted(start)
onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full max-w-md space-y-6 text-center">
      <PageHeroIcon :icon="['fas', 'fingerprint']"/>
      <PageHeader class="text-2xl font-bold">{{ t('passkeys.device.title') }}</PageHeader>

      <Spinner v-if="phase === 'loading'" size="lg" class="mx-auto"/>

      <template v-else-if="phase === 'waiting' && request">
        <p>{{ t('passkeys.device.instruction') }}</p>
        <div class="text-4xl font-mono tracking-widest">{{ groupedCode }}</div>
        <img :src="`data:image/png;base64,${request.qrPng}`" :alt="t('passkeys.device.qrAlt')"
             class="mx-auto h-48 w-48 rounded bg-white p-2"/>
        <MutedText tag="p" size="sm">{{ t('passkeys.device.qrHint') }}</MutedText>
        <MutedText tag="p" size="sm">{{ t('passkeys.device.waiting') }}</MutedText>
      </template>

      <p v-else-if="phase === 'enrolling'">{{ t('passkeys.create.preparing') }}</p>
      <p v-else-if="phase === 'signingIn' || phase === 'done'">{{ t('passkeys.device.signingIn') }}</p>

      <template v-else>
        <Alert variant="error">{{ error || t('passkeys.device.expired') }}</Alert>
        <PrimaryButton class="w-full" @click="start">{{ t('passkeys.device.retry') }}</PrimaryButton>
        <router-link class="block text-sm text-(--text-muted) hover:text-(--text)" to="/login">
          {{ t('passkeys.device.backToLogin') }}
        </router-link>
      </template>
    </div>
  </div>
</template>

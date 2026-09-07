/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {passkeys} from '@/api'
import {createWebAuthnCredential, getWebAuthnCredential, webauthnErrorKey} from '@/util/webauthn'
import {decideSignInLanding} from '@/util/signInLanding'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'

/**
 * The one-time offer after a sign-in: three sentences, the three fears answered, and a way past
 * it in one click. Creation runs straight into the trial while the member is still in front of
 * the screen; a cancelled trial costs nothing, because they never stopped being signed in.
 */
const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

type Phase = 'offer' | 'creating' | 'trial' | 'trialRunning' | 'done' | 'trialSkipped'
const phase = ref<Phase>('offer')
const error = ref('')

async function continueToApp() {
  const redirectPath = route.query.redirect as string | undefined
  clearActiveStation()
  clearActiveCluster()
  const landing = await decideSignInLanding(redirectPath)
  if (landing.stationId) setActiveStation(landing.stationId)
  if (landing.clusterUid) setActiveCluster(landing.clusterUid)
  await navigateTo(landing.path)
}

async function accept() {
  error.value = ''
  phase.value = 'creating'
  try {
    const begin = await passkeys.passkeyCreateBegin()
    const credentialJson = await createWebAuthnCredential(begin.optionsJson)
    await passkeys.passkeyCreateFinish(begin.challengeToken, credentialJson, t('passkeys.defaultLabel'))
    phase.value = 'trial'
  } catch (e) {
    // A cancelled OS sheet is not an error: nothing changed, and the offer stands.
    error.value = t(webauthnErrorKey(e, 'create'))
    phase.value = 'offer'
  }
}

async function runTrial() {
  error.value = ''
  phase.value = 'trialRunning'
  try {
    const begin = await passkeys.trialBegin()
    const credentialJson = await getWebAuthnCredential(begin.optionsJson)
    const outcome = await passkeys.trialFinish(begin.challengeToken, credentialJson)
    if (outcome === 'OK') {
      phase.value = 'done'
    } else if (outcome === 'FOREIGN_CREDENTIAL') {
      error.value = t('passkeys.errors.foreignCredential')
      phase.value = 'trial'
    } else {
      phase.value = 'trialSkipped'
    }
  } catch {
    phase.value = 'trialSkipped'
  }
}

async function answer(kind: 'LATER' | 'DECLINED') {
  try {
    await passkeys.answerOffer(kind)
  } finally {
    await continueToApp()
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full max-w-md space-y-6 text-center">
      <PageHeroIcon :icon="['fas', 'fingerprint']"/>
      <PageHeader class="text-2xl font-bold">{{ t('passkeys.offer.title') }}</PageHeader>

      <template v-if="phase === 'offer'">
        <p>{{ t('passkeys.offer.body') }}</p>
        <ul class="space-y-2 text-left text-sm">
          <li>{{ t('passkeys.offer.bulletBiometrics') }}</li>
          <li>{{ t('passkeys.offer.bulletPassword') }}</li>
          <li>{{ t('passkeys.offer.bulletRemovable') }}</li>
        </ul>
        <Alert v-if="error" variant="info">{{ error }}</Alert>
        <div class="space-y-2">
          <PrimaryButton class="w-full" @click="accept">{{ t('passkeys.offer.accept') }}</PrimaryButton>
          <SecondaryButton class="w-full" @click="answer('LATER')">{{ t('passkeys.offer.later') }}</SecondaryButton>
          <LinkButton class="w-full" @click="answer('DECLINED')">{{ t('passkeys.offer.decline') }}</LinkButton>
        </div>
      </template>

      <template v-else-if="phase === 'creating'">
        <p>{{ t('passkeys.create.preparing') }}</p>
      </template>

      <template v-else-if="phase === 'trial' || phase === 'trialRunning'">
        <p>{{ t('passkeys.create.trialPrompt') }}</p>
        <Alert v-if="error" variant="info">{{ error }}</Alert>
        <div class="space-y-2">
          <PrimaryButton class="w-full" :disabled="phase === 'trialRunning'" @click="runTrial">
            {{ t('passkeys.create.trialRun') }}
          </PrimaryButton>
          <SecondaryButton class="w-full" :disabled="phase === 'trialRunning'" @click="phase = 'trialSkipped'">
            {{ t('passkeys.create.trialSkip') }}
          </SecondaryButton>
        </div>
      </template>

      <template v-else>
        <p>{{ phase === 'done' ? t('passkeys.create.trialOk') : t('passkeys.create.trialSkipped') }}</p>
        <PrimaryButton class="w-full" @click="continueToApp">{{ t('passkeys.create.done') }}</PrimaryButton>
      </template>

      <MutedText tag="p" size="sm">{{ t('passkeys.explainer') }}</MutedText>
    </div>
  </div>
</template>

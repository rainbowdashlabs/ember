/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {passkeys} from '@/api'
import {createWebAuthnCredential, getWebAuthnCredential, webauthnErrorKey} from '@/util/webauthn'
import {decideSignInLanding} from '@/util/signInLanding'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'
import {useLoginConsent} from '@/composables/useLoginConsent'
import {useLinkRequests} from '@/composables/useLinkRequests'
import ConsentStep from '@/components/consent/ConsentStep.vue'

/**
 * The screen behind every enrolment token: the guardian's QR, the member manager's code, the
 * re-onboarding mail and the console line all land here. It names whose account the token
 * opens, the member confirms, their device asks for the fingerprint, and then they are signed
 * in, because they now hold a passkey and signing in with it is the ordinary path.
 *
 * <p>Since this signs the member in without the sign-in form, it asks the same consent first.
 */
const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()
const legal = useLoginConsent()
const linkRequests = useLinkRequests()
const {consent} = legal

type Phase = 'code' | 'confirm' | 'creating' | 'signingIn' | 'failed'
const phase = ref<Phase>('code')
const code = ref('')
const name = ref('')
const error = ref('')

/** Whether the setup screen sent us here; a legacy member may still want the password path. */
const cameFromSetup = route.query.fromSetup === '1'

onMounted(() => {
  void legal.loadConsentText()
  const fromQuery = route.query.code as string | undefined
  if (fromQuery) {
    code.value = fromQuery
    void lookup()
  }
})

async function lookup() {
  error.value = ''
  try {
    const account = await passkeys.tokenEnrollLookup(code.value)
    name.value = `${account.firstName} ${account.lastName}`.trim()
    phase.value = 'confirm'
  } catch {
    error.value = t('passkeys.enroll.unknownCode')
    phase.value = 'code'
  }
}

async function enroll() {
  error.value = ''
  phase.value = 'creating'
  try {
    const begin = await passkeys.tokenEnrollBegin(code.value)
    const credentialJson = await createWebAuthnCredential(begin.optionsJson)
    await passkeys.tokenEnrollFinish(code.value, begin.challengeToken, credentialJson)
    await signIn()
  } catch (e) {
    error.value = t(webauthnErrorKey(e, 'create'))
    phase.value = 'failed'
  }
}

async function signIn() {
  phase.value = 'signingIn'
  try {
    const begin = await passkeys.passkeySignInBegin()
    const credentialJson = await getWebAuthnCredential(begin.optionsJson)
    await passkeys.passkeySignInFinish(begin.challengeToken, credentialJson, false)
    await legal.recordAfterLogin()
    await linkRequests.checkAfterSignIn()
    clearActiveStation()
    clearActiveCluster()
    const landing = await decideSignInLanding(undefined)
    if (landing.stationId) setActiveStation(landing.stationId)
    if (landing.clusterUid) setActiveCluster(landing.clusterUid)
    await navigateTo(landing.path)
  } catch (e) {
    error.value = t(webauthnErrorKey(e, 'get'))
    phase.value = 'failed'
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div class="w-full space-y-6 text-center" :class="consent === 'accepted' ? 'max-w-md' : 'max-w-5xl'">
      <PageHeroIcon :icon="['fas', 'fingerprint']"/>
      <PageHeader class="text-2xl font-bold">{{ t('passkeys.enroll.title') }}</PageHeader>

      <div v-if="consent !== 'accepted'" class="text-left">
        <ConsentStep :legal="legal"/>
      </div>

      <template v-else-if="phase === 'code'">
        <p>{{ t('passkeys.enroll.codePrompt') }}</p>
        <FailureAlert :message="error"/>
        <TextInput v-model="code" placeholder="K7RM-2WQD" class="font-mono tracking-widest"/>
        <PrimaryButton class="w-full" :disabled="!code.trim()" @click="lookup">
          {{ t('common.continue') }}
        </PrimaryButton>
      </template>

      <template v-else-if="phase === 'confirm'">
        <p>{{ t('passkeys.enroll.confirm', {name}) }}</p>
        <MutedText tag="p" size="sm">{{ t('passkeys.explainer') }}</MutedText>
        <p>{{ t('passkeys.create.preparing') }}</p>
        <PrimaryButton class="w-full" @click="enroll">{{ t('passkeys.offer.accept') }}</PrimaryButton>
        <router-link v-if="cameFromSetup"
                     class="block text-sm text-(--text-muted) hover:text-(--text) transition-colors"
                     :to="{path: '/set-password', query: {token: code, password: '1'}}">
          {{ t('passkeys.enroll.setPasswordInstead') }}
        </router-link>
      </template>

      <p v-else-if="phase === 'creating'">{{ t('passkeys.create.preparing') }}</p>
      <p v-else-if="phase === 'signingIn'">{{ t('passkeys.device.signingIn') }}</p>

      <template v-else>
        <Alert variant="error">{{ error }}</Alert>
        <PrimaryButton class="w-full" @click="phase = 'confirm'">{{ t('passkeys.device.retry') }}</PrimaryButton>
      </template>
    </div>
  </div>
</template>

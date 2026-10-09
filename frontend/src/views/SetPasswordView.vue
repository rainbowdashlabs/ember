/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {auth} from '@/api'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'
import {describeFailure} from '@/util/failure'
import {decideSignInLanding} from '@/util/signInLanding'
import LinkNoLongerGood from './setpasswordview/LinkNoLongerGood.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ConsentStep from '@/components/consent/ConsentStep.vue'
import {useLoginConsent} from '@/composables/useLoginConsent'
import {useLinkRequests} from '@/composables/useLinkRequests'
import type {TokenStatus} from '@/api/generated/schema'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

/**
 * The consent asked before the password, as the sign-in form asks it before the sign-in. Setting a
 * password from a link signs the member in straight away, so without this step they would be in
 * without ever having been asked.
 */
const legal = useLoginConsent()
const linkRequests = useLinkRequests()
const {consent} = legal

const newPassword = ref('')
const confirmPassword = ref('')
const validationError = ref('')

const token = route.query.token as string

/**
 * What the link is worth, asked before the form is drawn.
 *
 * <p>Otherwise somebody types a password twice into a form that was never going to be accepted, and
 * is told only afterwards. While the answer is still coming nothing is shown but the spinner: a form
 * that appears and is then taken away reads worse than one that arrives a moment late.
 *
 * <p>The question is a courtesy. Where it cannot be answered at all the form is drawn anyway and the
 * submission decides, because a link that might be good must not be refused by a failed lookup.
 */
const status = ref<TokenStatus | null>(null)
const checking = ref(true)

/**
 * Sends the link on to passkey enrolment on a passwordless instance, where the setup mail's token
 * creates a passkey instead of asking for a password. A legacy member who still rotates a password
 * comes back with `?password=1`, and the server decides the rest. A failed lookup counts as not sent
 * on, because it must never block setting a password.
 *
 * @returns whether the link was sent on
 */
async function sentOnToEnrolment(): Promise<boolean> {
  if (route.query.password === '1') return false
  const {publicPasskeyMode} = await import('@/api/passkeys')
  if (await publicPasskeyMode() !== 'PASSWORDLESS') return false
  await navigateTo({path: '/enroll', query: {code: token, fromSetup: '1'}}, {replace: true})
  return true
}

onMounted(async () => {
  if (await sentOnToEnrolment().catch(() => false)) return
  void legal.loadConsentText()
  try {
    status.value = await auth.passwordLinkStatus(token)
  } catch {
    status.value = {standing: 'VALID', purpose: 'OTHER'}
  } finally {
    checking.value = false
  }
})

/**
 * Sets the password and goes wherever it leads.
 *
 * <p>Choosing a password proves what typing it into the sign-in form would prove, so the server
 * hands back a session and the reader carries on rather than signing in with the password they
 * chose ten seconds earlier. What it does not stand in for is a second factor: an account that has
 * one is sent to give it, exactly as signing in would, nor for an address the instance can write
 * to, which an administrator carrying none is sent to give first. An answer with none of the three
 * leaves the sign-in form to say what is still missing, which is where an unverified address is
 * explained.
 */
const {running: loading, error: submitError, run: runSetPassword} = useAsyncAction(async () => {
  const result = await auth.setPassword({token, password: newPassword.value})

  if (result.addressRequired && result.addressToken) {
    await router.push({path: '/set-address', query: {token: result.addressToken}})
    return
  }
  if (result.twoFactorRequired && result.preAuthToken) {
    await router.push({path: '/2fa-verify', query: {token: result.preAuthToken}})
    return
  }
  if (!auth.startedSession(result)) {
    await router.push({name: 'login'})
    return
  }

  await legal.recordAfterLogin()
  await linkRequests.checkAfterSignIn()
  clearActiveStation()
  clearActiveCluster()
  const landing = await decideSignInLanding()
  if (landing.stationId) setActiveStation(landing.stationId)
  if (landing.clusterUid) setActiveCluster(landing.clusterUid)
  await router.push(landing.path)
}, {formatError: (e) => describeFailure(e, t).message})

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
    <Spinner v-if="checking" size="lg"/>

    <LinkNoLongerGood v-else-if="status && status.standing !== 'VALID'" :status="status"/>

    <div v-else class="w-full space-y-6" :class="consent === 'accepted' ? 'max-w-xs' : 'max-w-5xl'">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'lock']"/>
        <PageHeader class="text-2xl font-bold">{{ t('setPassword.title') }}</PageHeader>
      </div>

      <ConsentStep :legal="legal"/>

      <form v-if="consent === 'accepted'" class="space-y-4" @submit.prevent="handleSetPassword">
        <FailureAlert :message="error"/>

        <div class="space-y-1">
          <FieldLabel>{{ t('setPassword.newPassword') }}</FieldLabel>
          <PasswordInput
              v-model="newPassword"
              :disabled="loading"
              :placeholder="t('setPassword.newPassword')"
              autocomplete="new-password"
          />
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('setPassword.confirmPassword') }}</FieldLabel>
          <PasswordInput
              v-model="confirmPassword"
              :disabled="loading"
              :placeholder="t('setPassword.confirmPassword')"
              autocomplete="new-password"
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

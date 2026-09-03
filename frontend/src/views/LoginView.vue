/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {auth, passkeys, adminSettings} from '@/api'
import {StorageDeniedError, type LoginResponse} from '@/api/auth'
import {acceptStorage, getItem} from '@/api/storage'
import {
  getWebAuthnCredential,
  getWebAuthnCredentialConditional,
  isConditionalMediationAvailable,
  isPlatformAuthenticatorAvailable,
  isWebAuthnSupported,
  webauthnErrorKey,
} from '@/util/webauthn'
import type {PasskeyModeName} from '@/api/adminSettings'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useDemoAccounts, type DemoAccount} from '@/composables/useDemoAccounts'
import {useLoginConsent} from '@/composables/useLoginConsent'
import DemoLogin from '@/views/loginview/DemoLogin.vue'
import ConsentGate from '@/views/loginview/ConsentGate.vue'
import LegalModal from '@/views/loginview/LegalModal.vue'
import LoginForm from '@/views/loginview/LoginForm.vue'
import DevDemoFooter from '@/views/loginview/DevDemoFooter.vue'
import {apiErrorMessage} from '@/util/apiError'
import {decideSignInLanding} from '@/util/signInLanding'

const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

const demo = useDemoAccounts()
const {
  isDemo, isDev, loading: demoLoading, activeStation, search, hasDemoAccounts, view: demoView,
} = demo

const legal = useLoginConsent()
const {
  consent, scopes, consentHtml, consentLoading,
  showPrivacyPolicy, privacyPolicyHtml, privacyPolicyLoading,
  showTos, tosHtml, tosLoading,
} = legal

const identifier = ref('')
const password = ref('')
/**
 * Whether the person signing in vouches for this machine. Carried into the second factor as well,
 * so ticking it is not undone by having two-factor enabled.
 */
const trustedDevice = ref(false)

/**
 * A sign-in form wants to be narrow; a consent text wants to be read. The gate carries the whole
 * consent document, which at the width of a password field is a column of three words. The demo
 * account grid needs room of its own, so the column widens for it while the form inside keeps its
 * own narrow width.
 */
const containerWidth = computed(() => {
  if (consent.value === null) return 'max-w-5xl'
  return isDemo.value || isDev.value ? 'max-w-2xl' : 'max-w-xs'
})
const registrationEnabled = ref(true)

/** The instance's effective passkey mode; OFF until the public read answers. */
const passkeyMode = ref<PasskeyModeName>('OFF')
const passkeySupported = isWebAuthnSupported()
const passkeyAvailable = computed(() =>
  passkeySupported && (passkeyMode.value === 'ENCOURAGED' || passkeyMode.value === 'PREFERRED' || passkeyMode.value === 'PASSWORDLESS'))

/**
 * Aborts the browser's passkey autofill when the form is submitted the ordinary way: one
 * conditional request may be pending per page, and the next screen would inherit it otherwise.
 */
let conditionalAbort: AbortController | null = null

onMounted(async () => {
  if (getItem('session_token')) {
    // Honor any pending deep link the router guard parked on /login - the user is already
    // authed and we don't want a fresh tab refresh to lose their target.
    const redirectPath = route.query.redirect as string | undefined
    navigateTo(redirectPath?.startsWith('/') ? redirectPath : '/station/dashboard/overview')
    return
  }

  legal.loadConsentText()
  adminSettings.isRegistrationEnabled().then(v => registrationEnabled.value = v).catch(() => {})

  await demo.load()
  // A demo instance stores nothing worth consenting to, so the gate is skipped there.
  if (isDemo.value) {
    acceptStorage()
    consent.value = 'accepted'
  }

  if (!isDemo.value) {
    passkeys.publicPasskeyMode().then(mode => {
      passkeyMode.value = mode
      if (passkeyAvailable.value) void startConditionalPasskey()
    }).catch(() => {})
  }
})

/**
 * Starts the browser's own passkey autofill: the member clicks the username field and their
 * name is offered. Silent on every failure, because this path was never asked for out loud;
 * the button and the password form are always there.
 */
async function startConditionalPasskey() {
  if (!(await isConditionalMediationAvailable())) return
  conditionalAbort = new AbortController()
  try {
    const begin = await passkeys.passkeySignInBegin()
    const credentialJson = await getWebAuthnCredentialConditional(begin.optionsJson, conditionalAbort.signal)
    const result = await passkeys.passkeySignInFinish(begin.challengeToken, credentialJson, trustedDevice.value)
    await completeSignIn(result)
  } catch {
    // Aborted by a password submit, dismissed, or unsupported after all: the form stands.
  }
}

/** What every successful sign-in does once a session (or its precondition) came back. */
async function completeSignIn(result: LoginResponse) {
  if (result.passwordChangeRequired && result.passwordChangeToken) {
    await navigateTo({path: '/set-password', query: {token: result.passwordChangeToken}})
    return
  }
  await legal.recordAfterLogin()
  if (await shouldOfferPasskey()) {
    const redirect = route.query.redirect as string | undefined
    await navigateTo({path: '/passkey-offer', query: redirect ? {redirect} : {}})
    return
  }
  await resolveStationAndRedirect()
}

/**
 * Whether the one-time offer screen comes next: the account's half is asked of the server, the
 * device's half (can this machine hold a passkey at all?) is probed here. Never after a passkey
 * sign-in, because that member plainly has one.
 */
async function shouldOfferPasskey(): Promise<boolean> {
  if (!passkeyAvailable.value) return false
  try {
    if (!(await isPlatformAuthenticatorAvailable())) return false
    return await passkeys.offerState()
  } catch {
    return false
  }
}

/**
 * Sends the user where they asked to go, or to the landing page that fits how many stations they
 * belong to.
 */
async function resolveStationAndRedirect() {
  const redirectPath = route.query.redirect as string | undefined
  clearActiveStation()
  clearActiveCluster()
  const landing = await decideSignInLanding(redirectPath)
  if (landing.stationId) setActiveStation(landing.stationId)
  if (landing.clusterUid) setActiveCluster(landing.clusterUid)
  await navigateTo(landing.path)
}

const {running: loggingIn, error: loginError, run: handleLogin} = useAsyncAction(async () => {
  if (!identifier.value || !password.value) return
  conditionalAbort?.abort()

  const result = await auth.login({
    identifier: identifier.value,
    password: password.value,
    trustedDevice: trustedDevice.value,
  })

  if (result.twoFactorRequired && result.preAuthToken) {
    const query: Record<string, string> = {token: result.preAuthToken}
    if (trustedDevice.value) query.trusted = '1'
    const redirect = route.query.redirect as string | undefined
    if (redirect) query.redirect = redirect
    await navigateTo({path: '/2fa-verify', query})
    return
  }

  await completeSignIn(result)
}, {formatError: (e) => {
  if (e instanceof StorageDeniedError) return t('login.storageDenied')
  return apiErrorMessage(e) || t('common.error')
}})

const {running: passkeySigningIn, error: passkeyError, run: handlePasskeyLogin} = useAsyncAction(async () => {
  conditionalAbort?.abort()
  const begin = await passkeys.passkeySignInBegin()
  const credentialJson = await getWebAuthnCredential(begin.optionsJson)
  const result = await passkeys.passkeySignInFinish(begin.challengeToken, credentialJson, trustedDevice.value)
  await completeSignIn(result)
}, {formatError: (e) => {
  if (e instanceof StorageDeniedError) return t('login.storageDenied')
  const key = webauthnErrorKey(e, 'get')
  if (key !== 'passkeys.errors.generic') return t(key)
  return apiErrorMessage(e) || t('login.passkeyFailed')
}})

const {running: demoLoggingIn, error: demoError, run: loginAsDemo} = useAsyncAction(async (account: DemoAccount) => {
  await auth.demoLogin(account.email)
  await resolveStationAndRedirect()
})

const loading = computed(() => loggingIn.value || demoLoggingIn.value || passkeySigningIn.value)
const error = computed(() => loginError.value || demoError.value || passkeyError.value)
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div :class="containerWidth" class="w-full space-y-6">
      <div v-if="!isDemo" class="text-center">
        <PageHeroIcon :icon="['fas', 'lock']"/>
        <PageHeader class="text-2xl font-bold">{{ t('login.title') }}</PageHeader>
      </div>

      <div v-if="demoLoading" class="flex justify-center">
        <Spinner size="lg"/>
      </div>

      <DemoLogin v-if="isDemo && !demoLoading"
                 v-model:active-station="activeStation" v-model:search="search"
                 :view="demoView" :error="error" :loading="loading" @login="loginAsDemo"/>

      <template v-if="!isDemo && !demoLoading">
        <ConsentGate v-if="consent === null"
                     v-model:scopes="scopes"
                     :consent-loading="consentLoading" :consent-html="consentHtml"
                     @accept="legal.acceptCurrentVersions" @deny="legal.deny"
                     @show-privacy="legal.loadPrivacyPolicy" @show-tos="legal.loadTos"/>

        <Alert v-if="consent === 'denied'" variant="error">
          {{ t('login.storageDenied') }}
        </Alert>

        <LegalModal v-model="showPrivacyPolicy" :title="t('storageConsent.privacyPolicyTitle')"
                    :loading="privacyPolicyLoading" :html="privacyPolicyHtml"/>

        <LegalModal v-model="showTos" :title="t('storageConsent.tosTitle')"
                    :loading="tosLoading" :html="tosHtml"/>

        <LoginForm v-if="consent === 'accepted'" class="mx-auto w-full max-w-xs"
                   v-model:identifier="identifier" v-model:password="password"
                   v-model:trustedDevice="trustedDevice"
                   :error="error" :loading="loading"
                   :registration-enabled="registrationEnabled"
                   :passkey-available="passkeyAvailable"
                   @submit="handleLogin" @passkey="handlePasskeyLogin"/>

        <DevDemoFooter v-if="isDev && hasDemoAccounts && consent === 'accepted'"
                       v-model:active-station="activeStation" v-model:search="search"
                       :view="demoView" :loading="loading" @login="loginAsDemo"/>
      </template>
    </div>
  </div>
</template>

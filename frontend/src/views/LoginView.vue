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
import {auth, session, adminSettings} from '@/api'
import {StorageDeniedError} from '@/api/auth'
import {acceptStorage, getItem} from '@/api/storage'
import {useStations} from '@/composables/useStations'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useDemoAccounts, type DemoAccount} from '@/composables/useDemoAccounts'
import {useLoginConsent} from '@/composables/useLoginConsent'
import {StationUserTypeLabels} from '@/api/types'
import DemoLogin from '@/views/loginview/DemoLogin.vue'
import ConsentGate from '@/views/loginview/ConsentGate.vue'
import LegalModal from '@/views/loginview/LegalModal.vue'
import LoginForm from '@/views/loginview/LoginForm.vue'
import DevDemoFooter from '@/views/loginview/DevDemoFooter.vue'
import {apiErrorMessage} from '@/util/apiError'

const {t} = useI18n()
const route = useRoute()
const {setActiveStation, clearActiveStation} = useStations()

const demo = useDemoAccounts()
const {
  isDemo, isDev, loading: demoLoading, activeStationTab, hasDemoAccounts,
  stationTabs, showStationTabs, roleGroups, noStationRoleGroups,
} = demo

const legal = useLoginConsent()
const {
  consent, consentHtml, consentLoading,
  showPrivacyPolicy, privacyPolicyHtml, privacyPolicyLoading,
  showTos, tosHtml, tosLoading,
} = legal

const email = ref('')
const password = ref('')
const registrationEnabled = ref(true)

onMounted(async () => {
  if (getItem('session_token')) {
    // Honor any pending deep link the router guard parked on /login — the user is already
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
})

/**
 * Sends the user where they asked to go, or to the landing page that fits how many stations they
 * belong to.
 */
async function resolveStationAndRedirect() {
  const redirectPath = route.query.redirect as string | undefined
  clearActiveStation()
  const [stations, info] = await Promise.all([
    session.getStations(),
    session.getSessionInfo().catch(() => null),
  ])
  const [onlyStation] = stations
  if (stations.length === 1 && onlyStation) {
    setActiveStation(onlyStation.stationId)
    await navigateTo(redirectPath || '/station/requirements')
  } else if (stations.length > 1) {
    await navigateTo(redirectPath || '/cross-station')
  } else if (info?.instanceUserType === 'ADMINISTRATOR') {
    await navigateTo(redirectPath || '/admin/dashboard/overview')
  } else {
    await navigateTo(redirectPath || '/account')
  }
}

const {running: loggingIn, error: loginError, run: handleLogin} = useAsyncAction(async () => {
  if (!email.value || !password.value) return

  const result = await auth.login({email: email.value, password: password.value})

  if (result.passwordChangeRequired && result.passwordChangeToken) {
    await navigateTo({path: '/set-password', query: {token: result.passwordChangeToken}})
    return
  }

  if (result.twoFactorRequired && result.preAuthToken) {
    const query: Record<string, string> = {token: result.preAuthToken}
    const redirect = route.query.redirect as string | undefined
    if (redirect) query.redirect = redirect
    await navigateTo({path: '/2fa-verify', query})
    return
  }

  await legal.recordAfterLogin()
  await resolveStationAndRedirect()
}, {formatError: (e) => {
  if (e instanceof StorageDeniedError) return t('login.storageDenied')
  return apiErrorMessage(e) || t('common.error')
}})

const {running: demoLoggingIn, error: demoError, run: loginAsDemo} = useAsyncAction(async (account: DemoAccount) => {
  await auth.demoLogin(account.email)
  await resolveStationAndRedirect()
})

const loading = computed(() => loggingIn.value || demoLoggingIn.value)
const error = computed(() => loginError.value || demoError.value)

function topRoleLabel(account: DemoAccount): string {
  return StationUserTypeLabels[account.userType as keyof typeof StationUserTypeLabels] ?? account.userType ?? 'Login'
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4 py-16">
    <div :class="isDemo || isDev ? 'max-w-2xl' : 'max-w-sm'" class="w-full space-y-6">
      <div v-if="!isDemo" class="text-center">
        <PageHeroIcon :icon="['fas', 'lock']"/>
        <PageHeader class="text-2xl font-bold">{{ t('login.title') }}</PageHeader>
      </div>

      <Spinner v-if="demoLoading" size="lg"/>

      <DemoLogin v-if="isDemo && !demoLoading"
                 :error="error" :loading="loading"
                 :no-station-role-groups="noStationRoleGroups" :role-groups="roleGroups"
                 :station-tabs="stationTabs" :show-station-tabs="showStationTabs"
                 v-model:active-station-tab="activeStationTab"
                 :role-label="topRoleLabel" @login="loginAsDemo"/>

      <template v-if="!isDemo && !demoLoading">
        <ConsentGate v-if="consent === null"
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

        <LoginForm v-if="consent === 'accepted'"
                   v-model:email="email" v-model:password="password"
                   :error="error" :loading="loading"
                   :registration-enabled="registrationEnabled"
                   @submit="handleLogin"/>

        <DevDemoFooter v-if="isDev && hasDemoAccounts && consent === 'accepted'"
                       :loading="loading"
                       :no-station-role-groups="noStationRoleGroups" :role-groups="roleGroups"
                       :station-tabs="stationTabs" :show-station-tabs="showStationTabs"
                       v-model:active-station-tab="activeStationTab"
                       :role-label="topRoleLabel" @login="loginAsDemo"/>
      </template>
    </div>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {auth, session, adminSettings} from '@/api'
import client from '@/api/client'
import {StorageDeniedError} from '@/api/auth'
import type {StorageConsent} from '@/api/storage'
import {acceptStorage, denyStorage, getConsent, getStoredLegalVersions, getItem, removeItem} from '@/api/storage'
import {useStations} from '@/composables/useStations'
import {useConsentGuard} from '@/composables/useConsentGuard'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import DemoLogin from '@/views/loginview/DemoLogin.vue'
import ConsentGate from '@/views/loginview/ConsentGate.vue'
import LegalModal from '@/views/loginview/LegalModal.vue'
import LoginForm from '@/views/loginview/LoginForm.vue'
import DevDemoFooter from '@/views/loginview/DevDemoFooter.vue'
import type {DemoAccount} from '@/views/loginview/demoTypes'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {setActiveStation} = useStations()

interface StationGroup {
  stationId: string
  stationName: string
  accounts: DemoAccount[]
}

const isDemo = ref(false)
const isDev = ref(false)
const registrationEnabled = ref(true)
const stationGroups = ref<StationGroup[]>([])
const noStationAccounts = ref<DemoAccount[]>([])
const activeStationTab = ref('')
const hasDemoAccounts = computed(() =>
    noStationAccounts.value.length > 0 || stationGroups.value.some(g => g.accounts.length > 0),
)
const demoAccounts = computed(() => {
  const group = stationGroups.value.find(g => g.stationId === activeStationTab.value)
  return group?.accounts ?? []
})
const demoLoading = ref(true)

const stationTabs = computed(() => stationGroups.value.map(g => ({key: g.stationId, label: g.stationName})))
const showStationTabs = computed(() => stationGroups.value.length > 1)

const noStationRoleGroups = computed(() => buildRoleGroups(noStationAccounts.value))

function buildRoleGroups(source: DemoAccount[]): { label: string; accounts: DemoAccount[] }[] {
  const groups: { label: string; accounts: DemoAccount[] }[] = []
  const seen = new Set<string>()

  function addGroup(label: string, filter: (a: DemoAccount) => boolean) {
    const matching = source.filter(a => !seen.has(a.email) && filter(a))
    if (matching.length > 0) {
      groups.push({label, accounts: matching})
      matching.forEach(a => seen.add(a.email))
    }
  }

  addGroup('Admin', a => a.userType === StationUserType.MANAGER)
  addGroup('Team', a => a.userType === StationUserType.TEAM)
  addGroup('Erziehungsberechtigter', a => a.userType === StationUserType.GUARDIAN)
  addGroup('Mitglieder', a => a.userType === StationUserType.MEMBER || a.userType === StationUserType.TRIAL)
  return groups
}

const roleGroups = computed(() => buildRoleGroups(demoAccounts.value))

onMounted(async () => {
  const token = getItem('session_token')
  if (token) {
    // Honor any pending deep link the router guard parked on /login — the
    // user is already authed and we don't want a fresh tab refresh to lose
    // their target.
    const redirectPath = route.query.redirect as string | undefined
    navigateTo(redirectPath && redirectPath.startsWith('/') ? redirectPath : '/station/dashboard/overview')
    return
  }

  loadConsentText()
  adminSettings.isRegistrationEnabled().then(v => registrationEnabled.value = v).catch(() => {})

  try {
    const res = await client.get<{ demo: boolean; dev: boolean }>('/demo/status')
    isDemo.value = res.data.demo
    isDev.value = res.data.dev
    if (isDemo.value) {
      acceptStorage()
      consent.value = 'accepted'
    }
    if (isDemo.value || isDev.value) {
      const accountsRes = await client.get<{ noStationAccounts: DemoAccount[]; stationGroups: StationGroup[] } | StationGroup[] | DemoAccount[]>('/demo/accounts')
      const payload = accountsRes.data
      if (Array.isArray(payload)) {
        if (payload.length > 0 && 'accounts' in payload[0]) {
          stationGroups.value = payload as StationGroup[]
        } else {
          stationGroups.value = [{stationId: 'default', stationName: 'Station', accounts: payload as DemoAccount[]}]
        }
        noStationAccounts.value = []
      } else {
        stationGroups.value = payload.stationGroups ?? []
        noStationAccounts.value = payload.noStationAccounts ?? []
      }
      if (stationGroups.value.length > 0) {
        activeStationTab.value = stationGroups.value[0].stationId
      }
    }
  } catch {
    isDemo.value = false
    isDev.value = false
  }
  demoLoading.value = false
})

async function resolveStationAndRedirect() {
  const redirectPath = route.query.redirect as string | undefined
  removeItem('station_id')
  const [stations, info] = await Promise.all([session.getStations(), session.getSessionInfo().catch(() => null)])
  const isAdmin = info?.instanceUserType === 'ADMINISTRATOR'
  if (stations.length === 1) {
    setActiveStation(stations[0].stationId)
    await navigateTo(redirectPath || '/station/requirements')
  } else if (stations.length > 1) {
    await navigateTo(redirectPath || '/cross-station')
  } else if (isAdmin) {
    await navigateTo(redirectPath || '/admin/dashboard/overview')
  } else {
    await navigateTo(redirectPath || '/account')
  }
}

const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const consent = ref<StorageConsent | null>(getConsent())
const consentHtml = ref('')
const consentVersion = ref('')
const privacyVersion = ref('')
const tosVersion = ref('')
const consentLoading = ref(false)
const showPrivacyPolicy = ref(false)
const privacyPolicyHtml = ref('')
const privacyPolicyLoading = ref(false)
const showTos = ref(false)
const tosHtml = ref('')
const tosLoading = ref(false)

async function loadConsentText() {
  consentLoading.value = true
  try {
    const [consentData, versions] = await Promise.all([
      session.getConsentText(),
      session.getLegalVersions(),
    ])
    consentHtml.value = consentData.html
    consentVersion.value = versions.consentVersion
    privacyVersion.value = versions.privacyVersion
    tosVersion.value = versions.tosVersion

    const stored = getStoredLegalVersions()
    if (consent.value === 'accepted' && stored.consent) {
      if (stored.consent !== versions.consentVersion
          || stored.privacy !== versions.privacyVersion
          || stored.tos !== versions.tosVersion) {
        consent.value = null
      }
    }
  } catch {
    consentHtml.value = ''
  }
  consentLoading.value = false
}

async function loadPrivacyPolicy() {
  if (privacyPolicyHtml.value) {
    showPrivacyPolicy.value = true
    return
  }
  privacyPolicyLoading.value = true
  showPrivacyPolicy.value = true
  try {
    const data = await session.getPrivacyPolicy()
    privacyPolicyHtml.value = data.html
  } catch {
    privacyPolicyHtml.value = ''
  }
  privacyPolicyLoading.value = false
}

async function loadTos() {
  if (tosHtml.value) {
    showTos.value = true
    return
  }
  tosLoading.value = true
  showTos.value = true
  try {
    const data = await session.getTermsOfService()
    tosHtml.value = data.html
  } catch {
    tosHtml.value = ''
  }
  tosLoading.value = false
}

function handleAccept() {
  acceptStorage({consent: consentVersion.value, privacy: privacyVersion.value, tos: tosVersion.value})
  consent.value = 'accepted'
}

function handleDeny() {
  denyStorage()
  consent.value = 'denied'
}

async function handleLogin() {
  error.value = ''

  if (!email.value || !password.value) {
    return
  }

  loading.value = true
  try {
    const result = await auth.login({email: email.value, password: password.value})

    if (result.passwordChangeRequired && result.passwordChangeToken) {
      await navigateTo({
        path: '/set-password',
        query: {token: result.passwordChangeToken},
      })
      return
    }

    if (result.twoFactorRequired && result.preAuthToken) {
      const query: Record<string, string> = {token: result.preAuthToken}
      const redirect = route.query.redirect as string | undefined
      if (redirect) query.redirect = redirect
      await navigateTo({path: '/2fa-verify', query})
      return
    }

    await checkAndRecordConsent()

    await resolveStationAndRedirect()
  } catch (e) {
    if (e instanceof StorageDeniedError) {
      error.value = t('login.storageDenied')
    } else if (e instanceof Error && 'response' in e) {
      const axiosErr = e as any
      error.value = axiosErr.response?.data?.message || t('common.error')
    } else {
      error.value = t('common.error')
    }
  } finally {
    loading.value = false
  }
}

async function checkAndRecordConsent() {
  try {
    const status = await session.getConsentStatus()

    if (!status.consented) {
      await session.recordConsent({
        consentVersion: consentVersion.value || status.currentConsentVersion,
        privacyVersion: privacyVersion.value || status.currentPrivacyVersion,
        tosVersion: tosVersion.value || status.currentTosVersion,
      })
      acceptStorage({
        consent: status.currentConsentVersion,
        privacy: status.currentPrivacyVersion,
        tos: status.currentTosVersion,
      })
    } else if (!status.current) {
      const {setNeedsReconsent} = useConsentGuard()
      setNeedsReconsent(true)
    } else {
      acceptStorage({
        consent: status.currentConsentVersion,
        privacy: status.currentPrivacyVersion,
        tos: status.currentTosVersion,
      })
    }
  } catch {
    error.value = ''
  }
}

async function loginAsDemo(account: DemoAccount) {
  loading.value = true
  error.value = ''
  try {
    // Quick login skips password verification entirely on the backend (dev / demo only) so the
    // click-to-impersonate flow keeps working after a seeded user has rotated their password.
    // No password-change or 2FA branch here — the backend short-circuits to a fully-verified
    // session.
    await auth.demoLogin(account.email)
    await resolveStationAndRedirect()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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
                     @accept="handleAccept" @deny="handleDeny"
                     @show-privacy="loadPrivacyPolicy" @show-tos="loadTos"/>

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

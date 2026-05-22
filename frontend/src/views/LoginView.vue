/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {auth, session, adminSettings} from '@/api'
import client from '@/api/client'
import {StorageDeniedError} from '@/api/auth'
import type {StorageConsent} from '@/api/storage'
import {acceptStorage, denyStorage, getConsent, getStoredLegalVersions, getItem} from '@/api/storage'
import {useStations} from '@/composables/useStations'
import {useConsentGuard} from '@/composables/useConsentGuard'
import {Roles} from '@/api/types'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {setActiveStation} = useStations()

interface DemoAccount {
  email: string
  firstName: string
  lastName: string
  roles: string[]
  groups: string[]
  tags: string[]
  profileComplete: boolean
}

const isDemo = ref(false)
const isDev = ref(false)
const registrationEnabled = ref(true)
const hasDemoAccounts = computed(() => demoAccounts.value.length > 0)
const demoAccounts = ref<DemoAccount[]>([])
const demoLoading = ref(true)

const roleFriendlyNames: Record<string, string> = {
  ADMIN: 'Admin',
  MANAGER: 'Manager',
  TEAM: 'Team',
  MEMBER_MANAGEMENT: 'Mitgliederverwaltung',
  ATTENDENCE_MANAGEMENT: 'Anwesenheit',
  EVENT_MANAGEMENT: 'Termine',
  INVENTORY_MANAGEMENT: 'Inventar',
  GUARDIAN: 'Erziehungsberechtigter',
  MEMBER: 'Mitglied',
  LOGIN: 'Login',
  NEWS_MANAGEMENT: 'Neuigkeiten',
}

const roleGroups = computed(() => {
  const groups: { label: string; accounts: DemoAccount[] }[] = []
  const seen = new Set<string>()

  function addGroup(label: string, filter: (a: DemoAccount) => boolean) {
    const matching = demoAccounts.value.filter(a => !seen.has(a.email) && filter(a))
    if (matching.length > 0) {
      groups.push({label, accounts: matching})
      matching.forEach(a => seen.add(a.email))
    }
  }

  addGroup('Admin', a => a.roles.includes(Roles.MANAGER) || a.roles.includes(Roles.ADMIN))
  addGroup('Team', a => a.roles.includes(Roles.TEAM))
  addGroup('Erziehungsberechtigter', a => a.roles.includes(Roles.GUARDIAN))
  addGroup('Mitglieder', a => a.roles.includes(Roles.MEMBER) || a.roles.includes(Roles.LOGIN))
  return groups
})

onMounted(async () => {
  const token = getItem('session_token')
  if (token) {
    router.push({name: 'dashboard-overview'})
    return
  }

  // Load consent text and registration status in parallel with demo status
  loadConsentText()
  adminSettings.isRegistrationEnabled().then(v => registrationEnabled.value = v).catch(() => {})

  try {
    const res = await client.get<{ demo: boolean; dev: boolean }>('/demo/status')
    isDemo.value = res.data.demo
    isDev.value = res.data.dev
    if (isDemo.value) {
      // Auto-accept storage in demo mode
      acceptStorage()
      consent.value = 'accepted'
    }
    if (isDemo.value || isDev.value) {
      const accountsRes = await client.get<DemoAccount[]>('/demo/accounts')
      demoAccounts.value = accountsRes.data
    }
  } catch { /* not demo/dev */
  }
  demoLoading.value = false
})

async function resolveStationAndRedirect() {
  const redirectPath = route.query.redirect as string | undefined
  const stations = await session.getStations()
  if (stations.length === 1) {
    setActiveStation(stations[0].stationId)
    await router.push(redirectPath || {name: 'dashboard-overview'})
  } else if (stations.length > 1) {
    await router.push({name: 'station-select', query: redirectPath ? {redirect: redirectPath} : undefined})
  } else {
    await router.push(redirectPath || {name: 'dashboard-overview'})
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

    // If consent was already accepted but any version changed, re-prompt
    const stored = getStoredLegalVersions()
    if (consent.value === 'accepted' && stored.consent) {
      if (stored.consent !== versions.consentVersion
          || stored.privacy !== versions.privacyVersion
          || stored.tos !== versions.tosVersion) {
        consent.value = null
      }
    }
  } catch { /* use fallback */ }
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
  } catch { /* ignore */ }
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
  } catch { /* ignore */ }
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
      await router.push({
        name: 'set-password',
        query: {token: result.passwordChangeToken},
      })
      return
    }

    // Check consent status after successful login
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
      // First login or no consent record — auto-create from the consent the user just accepted
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
      // Consent exists but documents changed — redirect to re-consent
      const {setNeedsReconsent} = useConsentGuard()
      setNeedsReconsent(true)
    } else {
      // Consent is current — update local storage versions
      acceptStorage({
        consent: status.currentConsentVersion,
        privacy: status.currentPrivacyVersion,
        tos: status.currentTosVersion,
      })
    }
  } catch { /* best effort — don't block login if consent check fails */ }
}

async function loginAsDemo(account: DemoAccount) {
  loading.value = true
  error.value = ''
  try {
    await auth.login({email: account.email, password: 'demo'})
    await resolveStationAndRedirect()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function topRoleLabel(account: DemoAccount): string {
  for (const role of account.roles) {
    if (roleFriendlyNames[role] && role !== Roles.LOGIN) return roleFriendlyNames[role]
  }
  return 'Login'
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div :class="isDemo || isDev ? 'max-w-2xl' : 'max-w-sm'" class="w-full space-y-6">
      <div v-if="!isDemo" class="text-center">
        <font-awesome-icon :icon="['fas', 'lock']" class="text-4xl text-primary mb-3"/>
        <h1 class="text-2xl font-bold">{{ t('login.title') }}</h1>
      </div>

      <Spinner v-if="demoLoading" size="lg"/>

      <!-- Demo mode: user picker only, no login form -->
      <template v-if="isDemo && !demoLoading">
        <div class="text-center">
          <font-awesome-icon :icon="['fas', 'fire']" class="text-4xl text-primary mb-3"/>
          <h1 class="text-2xl font-bold">{{ t('demo.title') }}</h1>
          <p class="text-sm text-(--text-muted) mt-1">{{ t('demo.loginHint') }}</p>
        </div>
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div v-for="group in roleGroups" :key="group.label" class="space-y-2">
          <SectionHeader>{{ group.label }}</SectionHeader>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2">
            <NeutralContainer
                v-for="account in group.accounts"
                :key="account.email"
                :class="{ 'opacity-50 pointer-events-none': loading }"
                class="cursor-pointer hover:border-primary transition-colors py-2 px-3"
                @click="loginAsDemo(account)"
            >
              <div class="font-medium text-sm">
                {{ account.firstName }} {{ account.lastName }}
                <ErrorBadge v-if="!account.profileComplete" class="ml-1 text-[10px]">{{ t('login.incomplete') }}</ErrorBadge>
              </div>
              <div class="text-xs text-(--text-muted)">{{ topRoleLabel(account) }}</div>
              <div v-if="account.groups.length > 0" class="flex flex-wrap gap-1 mt-1">
                <span v-for="g in account.groups" :key="g"
                      class="inline-block rounded-full px-1.5 py-0 text-[10px] bg-secondary/15 text-secondary-accent">{{ g }}</span>
              </div>
              <div v-if="account.tags.length > 0" class="flex flex-wrap gap-1 mt-0.5">
                <span v-for="tag in account.tags" :key="tag"
                      class="inline-block rounded-full px-1.5 py-0 text-[10px] bg-primary/15 text-primary">{{ tag }}</span>
              </div>
            </NeutralContainer>
          </div>
        </div>
      </template>

      <!-- Normal / dev mode: login form -->
      <template v-if="!isDemo && !demoLoading">
        <NeutralContainer v-if="consent === null" class="space-y-4">
          <h2 class="font-semibold text-lg">{{ t('storageConsent.title') }}</h2>

          <Spinner v-if="consentLoading" size="sm"/>
          <div v-else-if="consentHtml" class="legal-content max-h-64 overflow-y-auto text-sm border border-(--border) rounded-lg p-3" v-html="consentHtml"/>
          <p v-else class="text-sm text-(--text-muted)">{{ t('storageConsent.description') }}</p>

          <div class="flex gap-4">
            <button class="text-xs text-primary hover:underline cursor-pointer" @click="loadPrivacyPolicy">
              {{ t('storageConsent.privacyPolicy') }}
            </button>
            <button class="text-xs text-primary hover:underline cursor-pointer" @click="loadTos">
              {{ t('storageConsent.tos') }}
            </button>
          </div>

          <div class="flex gap-3">
            <SuccessButton class="flex-1" @click="handleAccept">
              {{ t('storageConsent.accept') }}
            </SuccessButton>
            <ErrorButton class="flex-1" @click="handleDeny">
              {{ t('storageConsent.deny') }}
            </ErrorButton>
          </div>
        </NeutralContainer>

        <Alert v-if="consent === 'denied'" variant="error">
          {{ t('login.storageDenied') }}
        </Alert>

        <!-- Privacy Policy Modal -->
        <Modal v-model="showPrivacyPolicy">
          <div class="space-y-4 p-4">
            <h3 class="text-lg font-semibold">{{ t('storageConsent.privacyPolicyTitle') }}</h3>
            <Spinner v-if="privacyPolicyLoading" size="sm"/>
            <div v-else-if="privacyPolicyHtml" class="legal-content max-h-[70vh] overflow-y-auto" v-html="privacyPolicyHtml"/>
            <div class="flex justify-end">
              <SecondaryButton @click="showPrivacyPolicy = false">{{ t('common.close') }}</SecondaryButton>
            </div>
          </div>
        </Modal>

        <!-- Terms of Service Modal -->
        <Modal v-model="showTos">
          <div class="space-y-4 p-4">
            <h3 class="text-lg font-semibold">{{ t('storageConsent.tosTitle') }}</h3>
            <Spinner v-if="tosLoading" size="sm"/>
            <div v-else-if="tosHtml" class="legal-content max-h-[70vh] overflow-y-auto" v-html="tosHtml"/>
            <div class="flex justify-end">
              <SecondaryButton @click="showTos = false">{{ t('common.close') }}</SecondaryButton>
            </div>
          </div>
        </Modal>

        <form v-if="consent === 'accepted'" class="space-y-4" @submit.prevent="handleLogin">
          <Alert v-if="error" variant="error">{{ error }}</Alert>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('login.email') }}</label>
            <TextInput
                v-model="email"
                :disabled="loading"
                :placeholder="t('login.email')"
            />
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('login.password') }}</label>
            <PasswordInput
                v-model="password"
                :disabled="loading"
                :placeholder="t('login.password')"
            />
          </div>

          <PrimaryButton
              :disabled="loading || !email || !password"
              class="w-full"
              @click="handleLogin"
          >
            {{ loading ? t('common.loading') : t('login.submit') }}
          </PrimaryButton>

          <router-link class="block w-full text-center text-sm text-(--text-muted) hover:text-(--text) transition-colors"
                       to="/forgot-password">
            {{ t('login.forgotPassword') }}
          </router-link>
          <router-link v-if="registrationEnabled" class="block w-full text-center text-sm text-primary hover:underline" to="/apply">
            {{ t('login.applyForStation') }}
          </router-link>
        </form>

        <!-- Dev mode: quick login picker below the form -->
        <template v-if="isDev && hasDemoAccounts && consent === 'accepted'">
          <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-4 mt-2">
            <p class="text-sm font-medium mb-3">{{ t('demo.devLoginHint') }}</p>
            <div v-for="group in roleGroups" :key="group.label" class="mb-3">
              <p class="text-xs font-semibold text-(--text-muted) mb-1">{{ group.label }}</p>
              <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-1.5">
                <NeutralContainer
                    v-for="account in group.accounts"
                    :key="account.email"
                    :class="{ 'opacity-50 pointer-events-none': loading }"
                    class="cursor-pointer hover:border-primary transition-colors py-1.5 px-2.5"
                    @click="loginAsDemo(account)"
                >
                  <div class="font-medium text-xs">
                    {{ account.firstName }} {{ account.lastName }}
                    <ErrorBadge v-if="!account.profileComplete" class="ml-1 text-[9px]">{{ t('login.incomplete') }}</ErrorBadge>
                  </div>
                  <div class="text-[10px] text-(--text-muted)">{{ topRoleLabel(account) }}</div>
                  <div v-if="account.groups.length > 0" class="flex flex-wrap gap-0.5 mt-0.5">
                    <span v-for="g in account.groups" :key="g"
                          class="inline-block rounded-full px-1 text-[9px] bg-secondary/15 text-secondary-accent">{{ g }}</span>
                  </div>
                  <div v-if="account.tags.length > 0" class="flex flex-wrap gap-0.5 mt-0.5">
                    <span v-for="tag in account.tags" :key="tag"
                          class="inline-block rounded-full px-1 text-[9px] bg-primary/15 text-primary">{{ tag }}</span>
                  </div>
                </NeutralContainer>
              </div>
            </div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

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
import {auth, session} from '@/api'
import client from '@/api/client'
import {StorageDeniedError} from '@/api/auth'
import type {StorageConsent} from '@/api/storage'
import {acceptStorage, denyStorage, getConsent, getItem} from '@/api/storage'
import {useStations} from '@/composables/useStations'
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
  MEMBER_MANAGER: 'Mitgliedsmanager',
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
  addGroup('Mitgliedsmanager', a => a.roles.includes(Roles.MEMBER_MANAGER))
  addGroup('Mitglieder', a => a.roles.includes(Roles.MEMBER) || a.roles.includes(Roles.LOGIN))
  return groups
})

onMounted(async () => {
  const token = getItem('session_token')
  if (token) {
    router.push({name: 'dashboard-overview'})
    return
  }

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

function handleAccept() {
  acceptStorage()
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
    } else {
      await resolveStationAndRedirect()
    }
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
      <div class="text-center">
        <font-awesome-icon :icon="['fas', 'lock']" class="text-4xl text-primary mb-3"/>
        <h1 class="text-2xl font-bold">{{ t('login.title') }}</h1>
      </div>

      <Spinner v-if="demoLoading" size="lg"/>

      <!-- Demo mode: user picker only -->
      <template v-if="isDemo && !demoLoading">
        <Alert variant="info">{{ t('demo.loginHint') }}</Alert>
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
          <h2 class="font-semibold">{{ t('storageConsent.title') }}</h2>
          <p class="text-sm text-[var(--text-muted)]">{{ t('storageConsent.description') }}</p>
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
          <router-link class="block w-full text-center text-sm text-primary hover:underline" to="/apply">
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

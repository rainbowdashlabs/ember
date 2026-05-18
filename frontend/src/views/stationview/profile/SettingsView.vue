/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {session as sessionApi, userSettings} from '@/api'
import type {ActiveSession, UserSettings, NotificationToggle} from '@/api/types'
import {useOnboardingTour} from '@/composables/useOnboardingTour'

const {t} = useI18n()
const router = useRouter()
const {startTour} = useOnboardingTour()

function restartTour() {
  startTour()
}

const settings = ref<UserSettings | null>(null)
const sessions = ref<ActiveSession[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)
const showInvalidateAllModal = ref(false)

interface NotifyRow {
  type: string
  label: string
  hint: string
}

const notifyRows: NotifyRow[] = [
  {type: 'NEW_NEWS', label: 'notifyNews', hint: 'notifyNewsHint'},
  {type: 'NEWS_COMMENT', label: 'notifyComments', hint: 'notifyCommentsHint'},
  {type: 'NEW_EVENT', label: 'notifyEvents', hint: 'notifyEventsHint'},
  {type: 'EVENT_REGISTRATION_STATUS', label: 'notifyEventStatus', hint: 'notifyEventStatusHint'},
  {type: 'EXCHANGE_STATUS_CHANGE', label: 'notifyExchanges', hint: 'notifyExchangesHint'},
  {type: 'MEMBER_ADDED_TO_GROUP', label: 'notifyGroups', hint: 'notifyGroupsHint'},
  {type: 'PROFILE_FIELD_CHANGED', label: 'notifyProfile', hint: 'notifyProfileHint'},
  {type: 'PROCUREMENT_REQUESTED', label: 'notifyProcurement', hint: 'notifyProcurementHint'},
]

function getToggle(type: string): NotificationToggle {
  return settings.value?.notifications?.[type] ?? {app: true, email: false}
}

// -- User agent parsing --

function parseOS(ua?: string): string {
  if (!ua) return 'unknown'
  if (ua.includes('Windows')) return 'windows'
  if (ua.includes('Mac OS') || ua.includes('Macintosh')) return 'mac'
  if (ua.includes('Android')) return 'android'
  if (ua.includes('iPhone') || ua.includes('iPad') || ua.includes('iOS')) return 'ios'
  if (ua.includes('Linux')) return 'linux'
  return 'unknown'
}

function parseBrowser(ua?: string): string {
  if (!ua) return 'unknown'
  if (ua.includes('Firefox')) return 'firefox'
  if (ua.includes('Edg/') || ua.includes('Edge')) return 'edge'
  if (ua.includes('OPR') || ua.includes('Opera')) return 'opera'
  if (ua.includes('Chrome') && !ua.includes('Edg')) return 'chrome'
  if (ua.includes('Safari') && !ua.includes('Chrome')) return 'safari'
  return 'unknown'
}

function osLabel(ua?: string): string {
  const os = parseOS(ua)
  const labels: Record<string, string> = {
    windows: 'Windows', mac: 'macOS', android: 'Android',
    ios: 'iOS', linux: 'Linux', unknown: t('userSettings.unknownDevice'),
  }
  return labels[os] ?? os
}

function browserLabel(ua?: string): string {
  const browser = parseBrowser(ua)
  const labels: Record<string, string> = {
    firefox: 'Firefox', chrome: 'Chrome', safari: 'Safari',
    edge: 'Edge', opera: 'Opera', unknown: t('userSettings.unknownBrowser'),
  }
  return labels[browser] ?? browser
}

function osIcon(ua?: string): string[] {
  const os = parseOS(ua)
  const icons: Record<string, [string, string]> = {
    windows: ['fab', 'windows'],
    mac: ['fab', 'apple'],
    linux: ['fab', 'linux'],
    android: ['fab', 'android'],
    ios: ['fab', 'apple'],
    unknown: ['fas', 'desktop'],
  }
  return icons[os] ?? ['fas', 'desktop']
}

function browserIcon(ua?: string): string[] {
  const browser = parseBrowser(ua)
  const icons: Record<string, [string, string]> = {
    chrome: ['fab', 'chrome'],
    firefox: ['fab', 'firefox-browser'],
    safari: ['fab', 'safari'],
    edge: ['fab', 'edge'],
    opera: ['fab', 'opera'],
    unknown: ['fas', 'globe'],
  }
  return icons[browser] ?? ['fas', 'globe']
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '–'
  return new Date(dateStr).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function timeAgo(dateStr?: string): string {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return t('userSettings.justNow')
  if (minutes < 60) return t('userSettings.minutesAgo', {n: minutes})
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('userSettings.hoursAgo', {n: hours})
  const days = Math.floor(hours / 24)
  return t('userSettings.daysAgo', {n: days})
}

// -- Data loading --

async function loadData() {
  loading.value = true
  try {
    const [s, sess] = await Promise.all([
      userSettings.getSettings(),
      sessionApi.getActiveSessions(),
    ])
    settings.value = s
    sessions.value = sess
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!settings.value) return
  saving.value = true
  saved.value = false
  error.value = ''
  try {
    settings.value = await userSettings.updateSettings({
      emailEnabled: settings.value.emailEnabled,
      notifications: settings.value.notifications,
    })
    saved.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function toggleEmailEnabled() {
  if (!settings.value) return
  settings.value.emailEnabled = !settings.value.emailEnabled
  save()
}

function toggleApp(type: string) {
  if (!settings.value) return
  const current = getToggle(type)
  settings.value.notifications[type] = {app: !current.app, email: current.email}
  save()
}

function toggleEmail(type: string) {
  if (!settings.value) return
  const current = getToggle(type)
  settings.value.notifications[type] = {app: current.app, email: !current.email}
  save()
}

async function invalidateSession(id: number) {
  error.value = ''
  try {
    await sessionApi.invalidateSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
  } catch {
    error.value = t('common.error')
  }
}

async function invalidateAll() {
  error.value = ''
  try {
    await sessionApi.invalidateAllSessions()
    showInvalidateAllModal.value = false
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('userSettings.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('userSettings.saved') }}</Alert>

      <template v-if="!loading && settings">
        <!-- Active Sessions -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('userSettings.sessions') }}</SubHeader>
            <ErrorButton class="text-xs" @click="showInvalidateAllModal = true">
              <font-awesome-icon :icon="['fas', 'trash']" class="mr-1"/>
              {{ t('userSettings.invalidateAll') }}
            </ErrorButton>
          </div>

          <div class="space-y-2">
            <NeutralContainer v-for="sess in sessions" :key="sess.id" class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="flex items-center gap-2">
                  <font-awesome-icon :icon="osIcon(sess.userAgent)" class="h-5 w-5 text-(--text-muted)"/>
                  <font-awesome-icon :icon="browserIcon(sess.userAgent)" class="h-5 w-5 text-(--text-muted)"/>
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium">{{ browserLabel(sess.userAgent) }}</span>
                    <span class="text-xs text-(--text-muted)">{{ osLabel(sess.userAgent) }}</span>
                    <span v-if="sess.isCurrent" class="text-xs font-semibold text-primary">{{ t('userSettings.currentSession') }}</span>
                  </div>
                  <p class="text-xs text-(--text-muted)">
                    {{ t('userSettings.lastActive') }}: {{ timeAgo(sess.lastUsedAt) }}
                    <span class="ml-2">{{ t('userSettings.created') }}: {{ formatDate(sess.createdAt) }}</span>
                  </p>
                </div>
              </div>
              <DeleteButton v-if="!sess.isCurrent" @click="invalidateSession(sess.id)"/>
            </NeutralContainer>
          </div>
        </NeutralContainer>

        <!-- Mail provider info -->
        <InfoContainer v-if="settings.mailConfigured" class="space-y-2">
          <p class="text-sm">
            {{
              t('userSettings.mailProviderInfo', {provider: settings.mailProviderName || t('userSettings.mailProviderUnknown')})
            }}
          </p>
          <p v-if="settings.mailProviderUrl" class="text-xs">
            <a :href="settings.mailProviderUrl" target="_blank" rel="noopener noreferrer"
               class="text-primary hover:underline">
              {{ t('userSettings.mailProviderPrivacy') }}
            </a>
          </p>
        </InfoContainer>

        <NeutralContainer v-if="!settings.mailConfigured" class="text-sm text-(--text-muted) py-3">
          {{ t('userSettings.mailNotConfigured') }}
        </NeutralContainer>

        <!-- Master email toggle -->
        <NeutralContainer class="space-y-4">
          <h3 class="font-semibold text-sm">{{ t('userSettings.emailTitle') }}</h3>
          <div class="flex items-center justify-between">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.emailEnabled') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.emailEnabledHint') }}</p>
              <p v-if="settings.mailConfigured" class="text-xs text-(--text-muted)">
                {{
                  t('userSettings.emailConsent', {provider: settings.mailProviderName || t('userSettings.mailProviderUnknown')})
                }}
              </p>
            </div>
            <ToggleInput :model-value="settings.emailEnabled" :disabled="!settings.mailConfigured"
                         @update:model-value="toggleEmailEnabled"/>
          </div>
        </NeutralContainer>

        <!-- Per-type notification toggles -->
        <NeutralContainer class="space-y-4">
          <h3 class="font-semibold text-sm">{{ t('userSettings.notifications') }}</h3>
          <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

          <!-- Header row -->
          <div
              class="grid grid-cols-[1fr_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
            <span></span>
            <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
            <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
          </div>

          <!-- Notification rows -->
          <div v-for="row in notifyRows" :key="row.type"
               class="grid grid-cols-[1fr_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t(`userSettings.${row.label}`) }}</span>
              <p class="text-xs text-(--text-muted)">{{ t(`userSettings.${row.hint}`) }}</p>
            </div>
            <div class="w-12 flex justify-center">
              <ToggleInput :model-value="getToggle(row.type).app" @update:model-value="toggleApp(row.type)"/>
            </div>
            <div class="w-12 flex justify-center">
              <ToggleInput :model-value="getToggle(row.type).email" :disabled="!settings.emailEnabled"
                           @update:model-value="toggleEmail(row.type)"/>
            </div>
          </div>
        </NeutralContainer>
        <!-- Restart Tour -->
        <NeutralContainer class="space-y-2">
          <SubHeader>{{ t('tour.restartButton') }}</SubHeader>
          <p class="text-sm text-(--text-muted)">{{ t('tour.restartHint') }}</p>
          <SecondaryButton @click="restartTour">
            <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1"/>
            {{ t('tour.restartButton') }}
          </SecondaryButton>
        </NeutralContainer>
      </template>

      <!-- Invalidate All Modal -->
      <Modal v-model="showInvalidateAllModal">
        <div class="space-y-4 p-4">
          <h3 class="text-lg font-semibold">{{ t('userSettings.invalidateAllTitle') }}</h3>
          <ErrorContainer>
            <p class="text-sm">{{ t('userSettings.invalidateAllWarning') }}</p>
          </ErrorContainer>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showInvalidateAllModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="invalidateAll">{{ t('userSettings.invalidateAll') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>

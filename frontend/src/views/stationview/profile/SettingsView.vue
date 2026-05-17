/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { userSettings } from '@/api'
import type { UserSettings, NotificationToggle } from '@/api/types'

const { t } = useI18n()

const settings = ref<UserSettings | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)

interface NotifyRow {
  type: string
  label: string
  hint: string
}

const notifyRows: NotifyRow[] = [
  { type: 'NEW_NEWS', label: 'notifyNews', hint: 'notifyNewsHint' },
  { type: 'NEWS_COMMENT', label: 'notifyComments', hint: 'notifyCommentsHint' },
  { type: 'NEW_EVENT', label: 'notifyEvents', hint: 'notifyEventsHint' },
  { type: 'EVENT_REGISTRATION_STATUS', label: 'notifyEventStatus', hint: 'notifyEventStatusHint' },
  { type: 'EXCHANGE_STATUS_CHANGE', label: 'notifyExchanges', hint: 'notifyExchangesHint' },
  { type: 'MEMBER_ADDED_TO_GROUP', label: 'notifyGroups', hint: 'notifyGroupsHint' },
  { type: 'PROFILE_FIELD_CHANGED', label: 'notifyProfile', hint: 'notifyProfileHint' },
  { type: 'PROCUREMENT_REQUESTED', label: 'notifyProcurement', hint: 'notifyProcurementHint' },
]

function getToggle(type: string): NotificationToggle {
  return settings.value?.notifications?.[type] ?? { app: true, email: false }
}

async function loadData() {
  loading.value = true
  try {
    settings.value = await userSettings.getSettings()
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
  settings.value.notifications[type] = { app: !current.app, email: current.email }
  save()
}

function toggleEmail(type: string) {
  if (!settings.value) return
  const current = getToggle(type)
  settings.value.notifications[type] = { app: current.app, email: !current.email }
  save()
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('userSettings.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('userSettings.saved') }}</Alert>

      <template v-if="!loading && settings">
        <!-- Mail provider info -->
        <InfoContainer v-if="settings.mailConfigured" class="space-y-2">
          <p class="text-sm">
            {{ t('userSettings.mailProviderInfo', { provider: settings.mailProviderName || t('userSettings.mailProviderUnknown') }) }}
          </p>
          <p v-if="settings.mailProviderUrl" class="text-xs">
            <a :href="settings.mailProviderUrl" target="_blank" rel="noopener noreferrer" class="text-primary hover:underline">
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
                {{ t('userSettings.emailConsent', { provider: settings.mailProviderName || t('userSettings.mailProviderUnknown') }) }}
              </p>
            </div>
            <ToggleInput :model-value="settings.emailEnabled" :disabled="!settings.mailConfigured" @update:model-value="toggleEmailEnabled" />
          </div>
        </NeutralContainer>

        <!-- Per-type notification toggles -->
        <NeutralContainer class="space-y-4">
          <h3 class="font-semibold text-sm">{{ t('userSettings.notifications') }}</h3>
          <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

          <!-- Header row -->
          <div class="grid grid-cols-[1fr_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
            <span></span>
            <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
            <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
          </div>

          <!-- Notification rows -->
          <div v-for="row in notifyRows" :key="row.type" class="grid grid-cols-[1fr_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t(`userSettings.${row.label}`) }}</span>
              <p class="text-xs text-(--text-muted)">{{ t(`userSettings.${row.hint}`) }}</p>
            </div>
            <div class="w-12 flex justify-center">
              <ToggleInput :model-value="getToggle(row.type).app" @update:model-value="toggleApp(row.type)" />
            </div>
            <div class="w-12 flex justify-center">
              <ToggleInput :model-value="getToggle(row.type).email" :disabled="!settings.emailEnabled" @update:model-value="toggleEmail(row.type)" />
            </div>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>

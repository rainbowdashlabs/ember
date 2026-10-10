/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {mailProviderLabel} from '@/util/mailProviders'
import MailProviderNotice from './MailProviderNotice.vue'
import type {NotificationToggle, NotificationType, SettingsResponse} from '@/api/generated/schema'

const props = defineProps<{
  settings: SettingsResponse
}>()

const emit = defineEmits<{
  toggleEmailEnabled: []
  toggleApp: [type: string]
  toggleEmail: [type: string]
  toggleFeed: [type: string]
}>()

const {t} = useI18n()

interface NotifyRow {
  type: NotificationType
  label: string
  hint: string
}

const allRows: NotifyRow[] = [
  {type: 'NEW_NEWS', label: 'notifyNews', hint: 'notifyNewsHint'},
  {type: 'NEWS_COMMENT', label: 'notifyComments', hint: 'notifyCommentsHint'},
  {type: 'COMMENT_MENTION', label: 'notifyMentions', hint: 'notifyMentionsHint'},
  {type: 'NEW_EVENT', label: 'notifyEvents', hint: 'notifyEventsHint'},
  {type: 'EVENT_REGISTRATION_STATUS', label: 'notifyEventStatus', hint: 'notifyEventStatusHint'},
  {type: 'MEMBER_ADDED_TO_GROUP', label: 'notifyGroups', hint: 'notifyGroupsHint'},
  {type: 'PROFILE_FIELD_CHANGED', label: 'notifyProfile', hint: 'notifyProfileHint'},
  {type: 'PROCUREMENT_REQUESTED', label: 'notifyProcurement', hint: 'notifyProcurementHint'},
  {type: 'EXPIRY_REMINDER', label: 'notifyExpiry', hint: 'notifyExpiryHint'},
]

/**
 * The rows for the types the server reported. The page sends back every type it switched, and the
 * server refuses a save naming one it does not know, so a row it did not report is never offered.
 */
const notifyRows = computed(() => allRows.filter(row => row.type in props.settings.notifications))

/** What switching mail on agrees to: the address goes to every provider the mail may pass through. */
const consent = computed(() => {
  const names = props.settings.mailProviders
      .map(provider => `„${mailProviderLabel(provider, t('userSettings.mailProviderOwnServer'))}"`)
  if (names.length > 1) return t('userSettings.emailConsentMany', {providers: names.join(', ')})
  return t('userSettings.emailConsent', {provider: names[0] ?? `„${t('userSettings.mailProviderUnknown')}"`})
})

function getToggle(type: string): NotificationToggle {
  return props.settings.notifications?.[type] ?? {app: true, email: false, feed: true}
}
</script>

<template>
  <MailProviderNotice v-if="settings.mailConfigured" :providers="settings.mailProviders"/>

  <NeutralContainer v-if="!settings.mailConfigured" class="text-sm text-(--text-muted) py-3">
    {{ t('userSettings.mailNotConfigured') }}
  </NeutralContainer>

  <NeutralContainer class="space-y-4">
    <SubHeader class="text-sm">{{ t('userSettings.emailTitle') }}</SubHeader>
    <div class="flex items-center justify-between">
      <div>
        <span class="text-sm font-medium">{{ t('userSettings.emailEnabled') }}</span>
        <p class="text-xs text-(--text-muted)">{{ t('userSettings.emailEnabledHint') }}</p>
        <p v-if="settings.mailConfigured" class="text-xs text-(--text-muted)">
          {{ consent }}
        </p>
      </div>
      <ToggleInput :model-value="settings.emailEnabled" :disabled="!settings.mailConfigured"
                   @update:model-value="emit('toggleEmailEnabled')"/>
    </div>
  </NeutralContainer>

  <NeutralContainer data-onboarding="notifications.matrix" class="space-y-4">
    <SubHeader class="text-sm">{{ t('userSettings.notifications') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

    <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
      <span/>
      <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
      <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
      <span class="w-12 text-center">{{ t('userSettings.columnFeed') }}</span>
    </div>

    <div v-for="row in notifyRows" :key="row.type"
         class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
      <div>
        <span class="text-sm font-medium">{{ t(`userSettings.${row.label}`) }}</span>
        <p class="text-xs text-(--text-muted)">{{ t(`userSettings.${row.hint}`) }}</p>
      </div>
      <div class="w-12 flex justify-center">
        <ToggleInput :model-value="getToggle(row.type).app" @update:model-value="emit('toggleApp', row.type)"/>
      </div>
      <div class="w-12 flex justify-center">
        <ToggleInput :model-value="getToggle(row.type).email" :disabled="!settings.emailEnabled"
                     @update:model-value="emit('toggleEmail', row.type)"/>
      </div>
      <div class="w-12 flex justify-center">
        <ToggleInput :model-value="getToggle(row.type).feed" @update:model-value="emit('toggleFeed', row.type)"/>
      </div>
    </div>
  </NeutralContainer>
</template>

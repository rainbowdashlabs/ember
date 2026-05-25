/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {UserSettings, NotificationToggle} from '@/api/types'

const props = defineProps<{
  settings: UserSettings
}>()

const emit = defineEmits<{
  toggleEmailEnabled: []
  toggleApp: [type: string]
  toggleEmail: [type: string]
  toggleFeed: [type: string]
}>()

const {t} = useI18n()

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
  return props.settings.notifications?.[type] ?? {app: true, email: false}
}
</script>

<template>
  <!-- Mail provider info -->
  <InfoContainer v-if="settings.mailConfigured" class="space-y-2">
    <p class="text-sm">
      {{ t('userSettings.mailProviderInfo', {provider: settings.mailProviderName || t('userSettings.mailProviderUnknown')}) }}
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
    <SubHeader class="text-sm">{{ t('userSettings.emailTitle') }}</SubHeader>
    <div class="flex items-center justify-between">
      <div>
        <span class="text-sm font-medium">{{ t('userSettings.emailEnabled') }}</span>
        <p class="text-xs text-(--text-muted)">{{ t('userSettings.emailEnabledHint') }}</p>
        <p v-if="settings.mailConfigured" class="text-xs text-(--text-muted)">
          {{ t('userSettings.emailConsent', {provider: settings.mailProviderName || t('userSettings.mailProviderUnknown')}) }}
        </p>
      </div>
      <ToggleInput :model-value="settings.emailEnabled" :disabled="!settings.mailConfigured"
                   @update:model-value="emit('toggleEmailEnabled')"/>
    </div>
  </NeutralContainer>

  <!-- Per-type notification toggles -->
  <NeutralContainer class="space-y-4">
    <SubHeader class="text-sm">{{ t('userSettings.notifications') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

    <!-- Header row -->
    <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
      <span></span>
      <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
      <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
      <span class="w-12 text-center">{{ t('userSettings.columnFeed') }}</span>
    </div>

    <!-- Notification rows -->
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

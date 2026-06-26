/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DummyNotificationRow from './DummyNotificationRow.vue'

const {t} = useI18n()

const dummyEmailEnabled = ref(true)

interface Row {
  label: string
  hint: string
  app: boolean
  email: boolean
  feed: boolean
}

const rows: Row[] = [
  {label: 'userSettings.notifyNews', hint: 'userSettings.notifyNewsHint', app: true, email: true, feed: true},
  {label: 'userSettings.notifyComments', hint: 'userSettings.notifyCommentsHint', app: true, email: false, feed: true},
  {label: 'userSettings.notifyEvents', hint: 'userSettings.notifyEventsHint', app: true, email: false, feed: false},
  {label: 'userSettings.notifyEventStatus', hint: 'userSettings.notifyEventStatusHint', app: true, email: true, feed: true},
  {label: 'userSettings.notifyExchanges', hint: 'userSettings.notifyExchangesHint', app: true, email: false, feed: false},
  {label: 'userSettings.notifyGroups', hint: 'userSettings.notifyGroupsHint', app: true, email: false, feed: true},
  {label: 'userSettings.notifyProfile', hint: 'userSettings.notifyProfileHint', app: true, email: false, feed: false},
  {label: 'userSettings.notifyProcurement', hint: 'userSettings.notifyProcurementHint', app: false, email: false, feed: false},
]
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader class="text-sm">{{ t('userSettings.emailTitle') }}</SubHeader>

    <div class="flex items-center justify-between">
      <div>
        <span class="text-sm font-medium">{{ t('userSettings.emailEnabled') }}</span>
        <p class="text-xs text-(--text-muted)">{{ t('userSettings.emailEnabledHint') }}</p>
        <p class="text-xs text-(--text-muted)">
          {{ t('userSettings.emailConsent', {provider: 'Beispiel Mail GmbH'}) }}
        </p>
      </div>
      <ToggleInput v-model="dummyEmailEnabled"/>
    </div>

    <template v-if="dummyEmailEnabled">
      <SubHeader class="text-sm pt-2">{{ t('userSettings.notifications') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

      <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
        <span></span>
        <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
        <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
        <span class="w-12 text-center">{{ t('userSettings.columnFeed') }}</span>
      </div>

      <div class="space-y-3">
        <DummyNotificationRow
          v-for="row in rows"
          :key="row.label"
          :label="row.label"
          :hint="row.hint"
          :app="row.app"
          :email="row.email"
          :feed="row.feed"
        />
      </div>
    </template>
  </NeutralContainer>
</template>

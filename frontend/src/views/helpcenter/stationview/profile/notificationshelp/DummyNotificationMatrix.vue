/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DummyNotificationRow from '../settingshelp/DummyNotificationRow.vue'

const {t} = useI18n()

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
    <SubHeader class="text-sm">{{ t('userSettings.notifications') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

    <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
      <span></span>
      <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
      <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
      <span class="w-12 text-center">{{ t('userSettings.columnFeed') }}</span>
    </div>

    <DummyNotificationRow
      v-for="row in rows"
      :key="row.label"
      :label="row.label"
      :hint="row.hint"
      :app="row.app"
      :email="row.email"
      :feed="row.feed"
    />
  </NeutralContainer>
</template>

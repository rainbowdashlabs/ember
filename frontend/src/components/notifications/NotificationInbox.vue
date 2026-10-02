/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, type RouteLocationRaw} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NotificationRow from './NotificationRow.vue'
import type {NotificationInboxApi} from '@/api/notifications'
import {useNotificationInbox} from '@/composables/useNotificationInbox'

/**
 * The unread notifications of one inbox, to open and to mark read: a station member's on the
 * dashboard, an association member's on the association's notifications page.
 *
 * <p>Whose inbox it is comes in as the api; everything else is the same for both. `onChanged` runs
 * after something was marked read, for the counters beside the menu entries. It is a prop rather
 * than an event because opening a notice leaves this page before the server has answered, and an
 * unmounted component's events are dropped, which left the counters standing.
 */
const props = defineProps<{
  api: NotificationInboxApi
  /** Where the gear in the header leads, or nothing where the settings are on the same page. */
  settings?: RouteLocationRaw | null
  /** Runs once something was marked read, also when that lands after the inbox has left the page. */
  onChanged?: () => void
}>()

const {t} = useI18n()
const router = useRouter()
const {notifications, loading, load, acknowledge, acknowledgeAll} =
    useNotificationInbox(props.api, () => props.onChanged?.())

function openSettings() {
  if (props.settings) router.push(props.settings)
}

onMounted(load)
</script>

<template>
  <NeutralContainer class="flex flex-col max-h-[66vh]">
    <div class="flex items-center justify-between mb-4 shrink-0">
      <SectionHeader>
        <font-awesome-icon :icon="['fas', 'bell']" class="mr-2"/>
        {{ t('dashboard.notifications') }}
        <span v-if="notifications.length > 0"> ({{ notifications.length }})</span>
      </SectionHeader>
      <div class="flex items-center gap-1">
        <SecondaryButton v-if="notifications.length > 0" :icon="['fas', 'check-double']" class="text-sm"
                         @click="acknowledgeAll">
          {{ t('dashboard.acknowledgeAll') }}
        </SecondaryButton>
        <IconButton
            v-if="settings"
            :icon="['fas', 'gear']"
            :label="t('dashboard.notificationSettings')"
            class="text-(--text-muted) hover:text-primary"
            @click="openSettings"
        />
      </div>
    </div>

    <div class="overflow-y-auto flex-1 space-y-2">
      <slot name="before-list"/>

      <EmptyState v-if="!loading && notifications.length === 0" compact>
        <font-awesome-icon :icon="['fas', 'check-double']" class="text-2xl text-success mb-2"/>
        <p>{{ t('dashboard.noNotifications') }}</p>
      </EmptyState>

      <NotificationRow v-for="entry in notifications" :key="entry.id" :notification="entry"
                       @acknowledge="acknowledge"/>
    </div>
  </NeutralContainer>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {notifications} from '@/api'
import {getFeedStatus, type FeedStatusResponse} from '@/api/feedToken'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import type {NotificationEntry} from '@/api/notifications'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const notifs = ref<NotificationEntry[]>([])
const loading = ref(true)
const feedStatus = ref<FeedStatusResponse | null>(null)

const typeIcons: Record<string, string> = {
  NEW_NEWS: 'newspaper',
  NEWS_COMMENT: 'comment',
  COMMENT_MENTION: 'at',
  EVENT_REGISTRATION_STATUS: 'calendar-days',
  EXCHANGE_STATUS_CHANGE: 'rotate',
  EXCHANGE_NEW_REQUEST: 'rotate',
  MOVEMENT_DECLINED: 'ban',
  NEW_EVENT: 'calendar-plus',
  NEW_EVENTS_BATCH: 'calendar-plus',
  MEMBER_ADDED_TO_GROUP: 'layer-group',
  PROFILE_FIELD_CHANGED: 'user',
  PROCUREMENT_REQUESTED: 'box-open',
  PROCUREMENT_FULFILLED: 'box-open',
  LOST_AND_FOUND_NEW: 'box-open',
  LOST_AND_FOUND_CLAIMED: 'box-open',
  LENDING_NEW_REQUEST: 'handshake',
  LENDING_STATUS_CHANGE: 'handshake',
  LENDING_NEW_MESSAGE: 'envelope',
  BOARD_TICKET_UPDATE: 'list-check',
  WAITLIST_NEW_ENTRY: 'list-ol',
  WAITLIST_PUBLIC_REGISTRATION: 'list-ol',
  STORAGE_WARNING: 'triangle-exclamation',
  CLUSTER_APPLICATION_SUBMITTED: 'sitemap',
  CLUSTER_APPLICATION_APPROVED: 'sitemap',
  CLUSTER_APPLICATION_DENIED: 'sitemap',
  CLUSTER_APPLICATION_WITHDRAWN: 'sitemap',
  CLUSTER_STATION_RELEASED: 'sitemap',
  CLUSTER_MODULE_DENIED: 'sitemap',
  CLUSTER_QUOTA_CHANGED: 'hard-drive',
  CLUSTER_MEMBER_ROLE_CHANGED: 'user-shield',
  CLUSTER_FIELD_VALUE_CHANGED: 'id-card',
  REGISTRATION_DEADLINE_EXPIRED: 'clock',
  EVENT_CANCELLED: 'calendar-xmark',
  EVENT_REMINDER: 'bell',
  PROCEDURE_ASSIGNED: 'clipboard-list',
  PROCEDURE_RESOLVED: 'clipboard-check',
  PROCEDURE_REOPENED: 'rotate',
  PROCEDURE_ITEM_CHECKED: 'square-check',
}

function renderMessage(n: NotificationEntry): string {
  const params = {...n.params}
  // Status fields arrive as raw enum names from the backend (PENDING, DONE, …);
  // route each one through its locale namespace so the message reads in German.
  if (n.type === 'EVENT_REGISTRATION_STATUS' && params.status) {
    params.status = t(`dashboard.registrationStatus.${params.status}`)
  }
  // A movement's step carries the words its own flow gives it, so there is nothing to look up.
  // The party it is waiting on is an enum and does need one.
  if (n.type === 'EXCHANGE_STATUS_CHANGE' && params.nextActor) {
    params.nextActor = t(`movements.actor.${params.nextActor}`)
  }
  if (n.type === 'LENDING_STATUS_CHANGE' && params.status) {
    params.status = t(`dashboard.lendingStatus.${params.status}`)
  }
  return t(n.localeKey, params)
}

async function navigateTo(n: NotificationEntry) {
  await ack(n.id)
  if (n.link) {
    router.push({name: n.link.route, params: n.link.routeParams})
  }
}

async function ack(id: number) {
  await notifications.acknowledge(id)
  notifs.value = notifs.value.filter(n => n.id !== id)
  refreshSidebarCounts()
}

async function ackAll() {
  await notifications.acknowledgeAll()
  notifs.value = []
  refreshSidebarCounts()
}

const showFeedCta = computed(() => {
  if (!feedStatus.value) return false
  return !feedStatus.value.hasToken || !feedStatus.value.notificationActive
})

const feedCtaMessage = computed(() => {
  if (!feedStatus.value) return ''
  if (!feedStatus.value.hasToken) return t('dashboard.feedSetupHint')
  return t('dashboard.feedInactiveHint')
})

async function loadData() {
  loading.value = true
  try {
    const [n, fs] = await Promise.all([
      notifications.listUnacknowledged(),
      getFeedStatus().catch(() => null),
    ])
    notifs.value = n
    feedStatus.value = fs
  } catch { /* ignore */ }
  loading.value = false
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer class="flex flex-col max-h-[66vh]">
    <div class="flex items-center justify-between mb-4 shrink-0">
      <SectionHeader>
        <font-awesome-icon :icon="['fas', 'bell']" class="mr-2"/>
        {{ t('dashboard.notifications') }}
        <span v-if="notifs.length > 0"> ({{ notifs.length }})</span>
      </SectionHeader>
      <div class="flex items-center gap-1">
        <SecondaryButton :icon="['fas', 'check-double']" v-if="notifs.length > 0" class="text-sm" @click="ackAll">
          {{ t('dashboard.acknowledgeAll') }}
        </SecondaryButton>
        <IconButton
            :icon="['fas', 'gear']"
            :label="t('dashboard.notificationSettings')"
            class="text-(--text-muted) hover:text-primary"
            @click="router.push({ name: 'profile-notifications' })"
        />
      </div>
    </div>

    <div class="overflow-y-auto flex-1 space-y-2">
      <InfoContainer v-if="showFeedCta" class="flex items-center justify-between gap-3 py-2 px-3">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'rss']" class="text-info shrink-0"/>
          <p class="text-xs">{{ feedCtaMessage }}</p>
        </div>
        <SecondaryButton class="shrink-0 text-xs" compact @click="router.push({ name: 'profile-notifications' })">
          {{ t('dashboard.feedSetup') }}
        </SecondaryButton>
      </InfoContainer>

      <EmptyState compact v-if="!loading && notifs.length === 0">
        <font-awesome-icon :icon="['fas', 'check-double']" class="text-2xl text-success mb-2"/>
        <p>{{ t('dashboard.noNotifications') }}</p>
      </EmptyState>

      <template v-if="notifs.length > 0">
        <NeutralContainer v-for="n in notifs" :key="n.id" class="flex items-start justify-between gap-3 py-2 px-3"
                          :class="{ 'cursor-pointer hover:bg-(--bg-accent)': n.link }" @click="navigateTo(n)">
          <div class="flex items-start gap-3">
            <font-awesome-icon :icon="['fas', typeIcons[n.type] ?? 'bell']"
                               class="text-primary mt-0.5 h-4 w-4 shrink-0"/>
            <div>
              <span class="text-xs font-semibold text-(--text-muted)">{{ t(`notification.typeLabel.${n.type}`) }}</span>
              <p class="text-sm">{{ renderMessage(n) }}</p>
              <!-- No body / preview snippet on the website: the dashboard panel stays
                   scannable and the full rich body lives in the feed only. -->
              <p class="text-xs text-(--text-muted)">{{ formatDateTime(n.createdAt) }}</p>
            </div>
          </div>
          <LinkButton class="shrink-0 mt-1" @click="($event: MouseEvent) => { $event.stopPropagation(); ack(n.id) }">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5"/>
            {{ t('dashboard.acknowledge') }}
          </LinkButton>
        </NeutralContainer>
      </template>
    </div>
  </NeutralContainer>
</template>

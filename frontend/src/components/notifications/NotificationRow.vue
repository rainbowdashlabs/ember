/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, type RouteLocationRaw} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import type {NotificationResponse, NotificationType} from '@/api/generated/schema'
import {formatDate, formatDateTime} from '@/util/format'
import {notificationMessageKey} from '@/util/notificationMessageKey'

const props = defineProps<{
  notification: NotificationResponse
}>()

const emit = defineEmits<{
  acknowledge: [id: number]
}>()

const {t} = useI18n()
const router = useRouter()

const typeIcons: Partial<Record<NotificationType, string>> = {
  NEW_NEWS: 'newspaper',
  NEWS_COMMENT: 'comment',
  COMMENT_MENTION: 'at',
  EVENT_REGISTRATION_STATUS: 'calendar-days',
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
  WAITLIST_INVITATION_ANSWERED: 'envelope-open-text',
  STORAGE_WARNING: 'triangle-exclamation',
  CLUSTER_APPLICATION_SUBMITTED: 'sitemap',
  CLUSTER_APPLICATION_APPROVED: 'sitemap',
  CLUSTER_APPLICATION_DENIED: 'sitemap',
  CLUSTER_APPLICATION_WITHDRAWN: 'sitemap',
  CLUSTER_STATION_RELEASED: 'sitemap',
  CLUSTER_MODULE_DENIED: 'sitemap',
  CLUSTER_QUOTA_CHANGED: 'hard-drive',
  CLUSTER_ITEM_ISSUED: 'truck',
  CLUSTER_ITEM_LOST: 'triangle-exclamation',
  CLUSTER_MEMBER_ROLE_CHANGED: 'user-shield',
  CLUSTER_FIELD_VALUE_CHANGED: 'id-card',
  EXPIRY_REMINDER: 'hourglass-half',
  REGISTRATION_DEADLINE_EXPIRED: 'clock',
  EVENT_CANCELLED: 'calendar-xmark',
  EVENT_REMINDER: 'bell',
  EVENT_DATE_DROPPED: 'calendar-xmark',
  EVENT_MOVED: 'calendar-days',
  EVENT_DATE_RESTORED: 'rotate-left',
  PROCEDURE_ASSIGNED: 'clipboard-list',
  PROCEDURE_RESOLVED: 'clipboard-check',
  PROCEDURE_REOPENED: 'rotate',
  PROCEDURE_ITEM_CHECKED: 'square-check',
  SELF_CHECK_ASSIGNED: 'shirt',
  SELF_CHECK_SUBMITTED: 'inbox',
  SELF_CHECK_ROW_REFUSED: 'rotate-left',
  FEDERATION_REQUEST_RECEIVED: 'share-nodes',
  FEDERATION_REQUEST_ACCEPTED: 'share-nodes',
  FEDERATION_REQUEST_DECLINED: 'share-nodes',
  NAME_CHANGE_REQUESTED: 'id-card',
  NAME_CHANGE_APPROVED: 'id-card',
  NAME_CHANGE_DENIED: 'id-card',
}

/** A day as the database writes one, which is not how anybody here reads one. */
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * The days in a notification's parameters, written the way the rest of the product writes them.
 *
 * <p>A date reaches the screen as the plain day it is stored as, and four kinds of notification
 * carry one: an appointment coming up, a batch of new appointments, an answer still wanted, a check
 * to hand back. Every one of them read "2026-09-19" in the middle of a German sentence. Done here
 * rather than at each of the four, because the next notification to carry a date would otherwise
 * read that way too until somebody noticed.
 */
function withReadableDates(params: Record<string, string>): Record<string, string> {
  return Object.fromEntries(
      Object.entries(params).map(([key, value]) =>
          [key, typeof value === 'string' && ISO_DATE.test(value) ? formatDate(value) : value]),
  )
}

/**
 * The sentence for the notification.
 *
 * <p>Most of it is the message the type carries, with the enums in its parameters routed through
 * their locale namespace so they read in German. A movement called off is the exception with two
 * sentences rather than one: whether the piece came home cannot be said in a word, and it is the
 * half the reader cannot guess.
 *
 * <p>The row shows this sentence and no body, so it stays scannable; the full body lives in the feed.
 */
const message = computed(() => {
  const n = props.notification
  const params = withReadableDates(n.params)
  if (n.type === 'EVENT_REGISTRATION_STATUS' && params.status) {
    params.status = t(`dashboard.registrationStatus.${params.status}`)
  }
  if (n.type === 'LENDING_STATUS_CHANGE' && params.status) {
    params.status = t(`dashboard.lendingStatus.${params.status}`)
  }
  if (n.type === 'MOVEMENT_CANCELLED') {
    const key = params.itemStayedAway === 'true' ? 'movementCancelledAway' : 'movementCancelled'
    return t(`notification.${key}`, params)
  }
  return t(notificationMessageKey(n.type, n.localeKey, params), params)
})

/**
 * What the notification is about, or nothing where it is about nothing the app can open. A link
 * naming a page the app does not know is shown as no link rather than as a row that does nothing
 * when pressed.
 */
const page = computed<RouteLocationRaw | null>(() => {
  const link = props.notification.link
  if (!link || !router.hasRoute(link.route)) return null
  return {name: link.route, params: routeValues(link.routeParams), query: routeValues(link.query)}
})

/**
 * The values a notification's link carries, as the router takes them. The server stores them as
 * whatever the sender put in, numbers among them, and a route reads every one as text anyway.
 */
function routeValues(values: Record<string, unknown> | null): Record<string, string> | undefined {
  if (!values) return undefined
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, String(value)]))
}
</script>

<template>
  <RowLink :to="page">
    <NeutralContainer data-testid="notification-entry"
                      class="flex items-start justify-between gap-3 py-2 px-3"
                      :class="{ 'cursor-pointer hover:bg-(--bg-accent)': page }"
                      @click="emit('acknowledge', notification.id)">
      <div class="flex items-start gap-3">
        <font-awesome-icon :icon="['fas', typeIcons[notification.type] ?? 'bell']"
                           class="text-primary mt-0.5 h-4 w-4 shrink-0"/>
        <div>
          <span class="text-xs font-semibold text-(--text-muted)">{{ t(`notification.typeLabel.${notification.type}`) }}</span>
          <p class="text-sm">{{ message }}</p>
          <p class="text-xs text-(--text-muted)">{{ formatDateTime(notification.createdAt) }}</p>
        </div>
      </div>
      <LinkButton class="shrink-0 mt-1" @click="emit('acknowledge', notification.id)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5"/>
        {{ t('dashboard.acknowledge') }}
      </LinkButton>
    </NeutralContainer>
  </RowLink>
</template>

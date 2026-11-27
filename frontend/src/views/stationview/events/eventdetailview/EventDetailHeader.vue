/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import {useNewsRoutes} from '@/composables/useNewsRoutes'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import DetailPageHeader from '../eventshared/DetailPageHeader.vue'
import {isRecurringEvent} from '@/api/events'
import {events} from '@/api'
import type {StationEvent} from '@/api/generated/schema'
import {eventTypeLabelKey} from '../eventshared/eventTypeLabel'
import {formatDate} from '@/util/format'
import {computed} from 'vue'

const props = defineProps<{
  event: StationEvent
  canManageEvents: boolean
  /** Whether the reader may create appointments, which is what copying this one hangs on. */
  canCreateEvents?: boolean
  /** Whether the reader may write news here, which is what the announcement entry hangs on. */
  canWriteNews: boolean
  /** The one occurrence the page is showing, which is the one an announcement is about. */
  effectiveDate: string | null
  /** Whether the occurrence on screen is off, on its own or with the whole series. */
  dateCancelled: boolean
  /** Whether the occurrence on screen is already behind the station, which nothing can call off or bring back. */
  datePast: boolean
  /** What the event is called a kind of, shown beside its name. Absent where it has no category. */
  categoryName?: string
  /** The sheet this occurrence already has, which is opened rather than taken again. */
  attendanceSessionId?: number | null
  /** Whether a sheet could be taken for this occurrence: the reader may, and it is today's. */
  canTakeAttendance?: boolean
  /** The event group the appointment is a part of, or nothing for an appointment of its own. */
  eventGroupName?: string | null
}>()

const emit = defineEmits<{
  'cancel-date': []
  'cancel-series': []
  'restore-date': []
  attendance: []
}>()

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()
const newsRoutes = useNewsRoutes()

/** How long the series runs, where it was given an end. A series without one shows nothing. */
const repeatEnd = computed(() => {
  if (!isRecurringEvent(props.event.eventType)) return ''
  if (props.event.repeatUntil) return t('events.repeatUntilLabel', {date: formatDate(props.event.repeatUntil)})
  if (props.event.repeatCount) return t('events.repeatCountLabel', {count: props.event.repeatCount})
  return ''
})

/** Whether the menu offers anything about the sheet of this occurrence. */
const hasAttendance = computed(() => props.attendanceSessionId != null || props.canTakeAttendance === true)

/** A date still to come that takes place can be called off; the rest of a series is left alone. */
const canCancelDate = computed(() =>
    props.canManageEvents && !!props.effectiveDate && !props.dateCancelled && !props.datePast)

/** A series that is not off yet can still be called off as a whole, which is final. */
const canCancelSeries = computed(() =>
    props.canManageEvents && isRecurringEvent(props.event.eventType) && !props.event.cancelled)

/** A date called off on its own can be brought back while it is still to come. A cancelled series stays off. */
const canRestoreDate = computed(() =>
    props.canManageEvents && props.dateCancelled && !props.event.cancelled && !props.datePast)

/**
 * Whether the actions menu holds anything besides deleting. Editing stays a button of its own, because
 * the appointment is opened to be changed; announcing and calling off are occasional, and calling off
 * and deleting come last and coloured, since a row under a harmless one reads as harmless otherwise.
 */
const hasMenuItems = computed(() =>
    props.canWriteNews || props.canCreateEvents || hasAttendance.value
    || canCancelDate.value || canCancelSeries.value || canRestoreDate.value)

/** The edit page on the date in view, for whoever manages appointments. */
const editTo = computed(() => props.canManageEvents
  ? {name: eventRoutes.edit, params: {id: props.event.id}, query: props.effectiveDate ? {date: props.effectiveDate} : {}}
  : null)

/**
 * What deleting asks, by what it deletes: a part leaves its event group, a repeating appointment goes
 * with every date, where calling off one date is what is usually meant, and a one-off is just itself.
 */
const deleteMessage = computed(() => {
  const name = props.event.name
  if (props.eventGroupName) return t('events.deletePartConfirm', {name, group: props.eventGroupName})
  if (isRecurringEvent(props.event.eventType)) return t('events.deleteRecurringConfirm', {name})
  return t('events.deleteEventConfirm', {name})
})

/** Deletes the appointment with every date and leaves for the appointment list, for whoever manages appointments. */
const remove = computed(() => props.canManageEvents
  ? async () => {
    await events.deleteEvent(props.event.id)
    await router.push({name: eventRoutes.index})
  }
  : null)

/** Opens a new appointment filled in from this one. */
function copyEvent() {
  router.push({name: eventRoutes.create, query: {copyFrom: String(props.event.id)}})
}

/**
 * Opens the news editor on a draft written from this appointment.
 *
 * <p>The appointment and the date travel in the address, so the editor reads both from the
 * server rather than trusting a handover, and so a reload does not lose the draft. Which occurrence
 * matters: announcing a weekly Tuesday without saying which one is exactly the mistake that gets
 * made when the date is retyped by hand.
 */
function announce() {
  const query: Record<string, string> = {event: String(props.event.id)}
  if (props.effectiveDate) query.date = props.effectiveDate
  router.push({name: newsRoutes.create, query})
}
</script>

<template>
  <DetailPageHeader
      :back-to="{name: 'events-upcoming'}"
      :edit-to="editTo"
      :edit-label="t('events.editEvent')"
      edit-test-id="event-edit"
      menu-test-id="event-actions"
      :has-menu-items="hasMenuItems"
      :remove="remove"
      :delete-message="deleteMessage"
      delete-test-id="event-delete"
  >
    <template #title>
      <SubHeader>{{ event.name }}</SubHeader>
      <SecondaryBadge v-if="event.categoryId && props.categoryName" data-testid="event-category">
        {{ props.categoryName }}
      </SecondaryBadge>
      <SecondaryBadge data-testid="event-type">
        <font-awesome-icon v-if="isRecurringEvent(event.eventType)" :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>{{ t(eventTypeLabelKey(event.eventType)) }}
      </SecondaryBadge>
      <SecondaryBadge v-if="repeatEnd" data-testid="event-repeat-end">{{ repeatEnd }}</SecondaryBadge>
      <ErrorBadge v-if="dateCancelled">{{ t('events.cancelled') }}</ErrorBadge>
    </template>
    <DropdownMenuItem v-if="canWriteNews" :icon="['fas', 'bullhorn']" data-testid="event-announce" @click="announce">
      {{ t('events.announceAsNews') }}
    </DropdownMenuItem>
    <DropdownMenuItem v-if="canCreateEvents" :icon="['fas', 'copy']" data-testid="event-copy" @click="copyEvent">
      {{ t('events.copyEvent') }}
    </DropdownMenuItem>
    <DropdownMenuItem v-if="hasAttendance" :icon="['fas', 'clipboard-user']" data-testid="event-attendance"
                      @click="emit('attendance')">
      {{ attendanceSessionId ? t('events.openAttendance') : t('events.takeAttendance') }}
    </DropdownMenuItem>
    <DropdownMenuItem v-if="canRestoreDate" :icon="['fas', 'rotate-left']" data-testid="event-restore-date"
                      @click="emit('restore-date')">
      {{ t('events.restoreDate') }}
    </DropdownMenuItem>
    <DropdownMenuItem v-if="canCancelDate" :icon="['fas', 'ban']" destructive data-testid="event-cancel-date"
                      @click="emit('cancel-date')">
      {{ t('events.cancelDate') }}
    </DropdownMenuItem>
    <DropdownMenuItem v-if="canCancelSeries" :icon="['fas', 'calendar-xmark']" destructive
                      data-testid="event-cancel-series" @click="emit('cancel-series')">
      {{ t('events.cancelSeries') }}
    </DropdownMenuItem>
  </DetailPageHeader>
</template>

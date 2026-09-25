/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import TabBar from '@/components/navigation/TabBar.vue'
import EventFilterBar from '../eventshared/EventFilterBar.vue'
import EventsCalendar from './EventsCalendar.vue'
import NeedsActionToggle from './NeedsActionToggle.vue'
import FederatedEventsSection from './FederatedEventsSection.vue'
import UpcomingHeaderBar from './UpcomingHeaderBar.vue'
import TodayEventsSection from './TodayEventsSection.vue'
import UpcomingEventsSection from './UpcomingEventsSection.vue'
import type {AnswerablePerson} from '@/util/eventAnswers'
import type {PagedListView} from '@/composables/usePagedList'
import {EventStates, type EventBreak, type EventCategory, type EventField, type EventRegistrationEntry, type StationEvent, type UpcomingEventOccurrence} from '@/api/events'

type ViewMode = 'list' | 'calendar'

/**
 * The upcoming page below its header bar.
 *
 * <p>The tabs belong to the list. The calendar shows both directions and always has, so it is not
 * one of them and does not move.
 */
const props = defineProps<{
  viewMode: ViewMode
  isPast: boolean
  canCreate: boolean
  canManageAttendance: boolean
  categories: EventCategory[]
  allEvents: StationEvent[]
  eventBreaks: EventBreak[]
  filteredTodayEvents: StationEvent[]
  occurrences: PagedListView<UpcomingEventOccurrence>
  overviewFields: Record<number, EventField[]>
  myRegistrations: EventRegistrationEntry[]
  managedMembersCount: number
  registering: boolean
  multiDayEndDate: (event: StationEvent, date: string) => string | null
  getRegistrationSummary: (eventId: number, date: string) => {accepted: number; pending: number; declined: number; total: number}
  getEligibleMembers: (eventId: number) => AnswerablePerson[]
  getRestrictionNote: (eventId: number) => string | null
  todayDetailRoute: (event: StationEvent) => RouteLocationRaw
  eventDetailRoute: (event: StationEvent, date: string) => RouteLocationRaw
  formatTime: (iso: string | null | undefined) => string
  formatDeadline: (iso: string) => string
}>()

defineEmits<{
  (e: 'update:view-mode', value: ViewMode): void
  (e: 'create'): void
  (e: 'attendance', event: StationEvent): void
  (e: 'register', event: StationEvent, date: string, people: AnswerablePerson[]): void
  (e: 'decline', event: StationEvent, date: string, people: AnswerablePerson[]): void
  (e: 'withdraw', regId: number): void
  (e: 'load-more'): void
}>()

const tab = defineModel<string>('tab', {required: true})
const search = defineModel<string>('search', {required: true})
const categoryId = defineModel<string>('categoryId', {required: true})
const needsAction = defineModel<boolean>('needsAction', {required: true})
const from = defineModel<string>('from', {required: true})
const to = defineModel<string>('to', {required: true})

const {t} = useI18n()

const tabs = computed(() => [
  {key: EventStates.CURRENT, label: t('eventsUpcoming.tabUpcoming')},
  {key: EventStates.PAST, label: t('eventsUpcoming.tabPast')},
])

const listTitle = computed(() => props.isPast ? t('eventsUpcoming.past') : t('eventsUpcoming.upcoming'))
const emptyMessage = computed(() => props.isPast ? t('eventsUpcoming.noPast') : t('eventsUpcoming.noUpcoming'))
</script>

<template>
  <UpcomingHeaderBar
      :view-mode="viewMode"
      :can-create="canCreate"
      @update:view-mode="$emit('update:view-mode', $event)"
      @create="$emit('create')"
  />

  <EventFilterBar
      v-model:search="search"
      v-model:category-id="categoryId"
      v-model:from="from"
      v-model:to="to"
      :categories="categories"
  >
    <NeedsActionToggle v-model="needsAction"/>
  </EventFilterBar>

  <EventsCalendar
      v-if="viewMode === 'calendar'"
      :all-events="allEvents"
      :event-breaks="eventBreaks"
      :selected-category-id="categoryId"
      :search-query="search"
      :categories="categories"
  />

  <template v-if="viewMode === 'list'">
    <TabBar v-model="tab" :tabs="tabs"/>

    <TodayEventsSection
        v-if="!isPast"
        :events="filteredTodayEvents"
        :overview-fields="overviewFields"
        :can-manage-attendance="canManageAttendance"
        :detail-route="todayDetailRoute"
        :format-time="formatTime"
        @attendance="$emit('attendance', $event)"
    />

    <FederatedEventsSection v-if="!isPast"/>

    <UpcomingEventsSection
        :title="listTitle"
        :empty-message="emptyMessage"
        :list="occurrences"
        :answerable="!isPast"
        :categories="categories"
        :overview-fields="overviewFields"
        :my-registrations="myRegistrations"
        :managed-members-count="managedMembersCount"
        :registering="registering"
        :multi-day-end-date="multiDayEndDate"
        :get-registration-summary="getRegistrationSummary"
        :get-eligible-members="getEligibleMembers"
        :get-restriction-note="getRestrictionNote"
        :detail-route="eventDetailRoute"
        :format-time="formatTime"
        :format-deadline="formatDeadline"
        @register="(event, date, people) => $emit('register', event, date, people)"
        @decline="(event, date, people) => $emit('decline', event, date, people)"
        @withdraw="regId => $emit('withdraw', regId)"
        @load-more="$emit('load-more')"
    />
  </template>
</template>

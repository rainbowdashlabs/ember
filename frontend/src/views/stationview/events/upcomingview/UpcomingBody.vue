/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import EventFilterBar from './EventFilterBar.vue'
import EventsCalendar from './EventsCalendar.vue'
import FederatedEventsSection from './FederatedEventsSection.vue'
import UpcomingHeaderBar from './UpcomingHeaderBar.vue'
import TodayEventsSection from './TodayEventsSection.vue'
import UpcomingEventsSection from './UpcomingEventsSection.vue'
import type {AnswerablePerson} from '@/util/eventAnswers'
import type {EventBreak, EventCategory, EventField, EventRegistrationEntry, StationEvent, UpcomingEventOccurrence} from '@/api/events'

type ViewMode = 'list' | 'calendar'

defineProps<{
  viewMode: ViewMode
  canCreate: boolean
  canManageAttendance: boolean
  searchQuery: string
  selectedCategoryId: string
  showNeedsAction: boolean
  categories: EventCategory[]
  allEvents: StationEvent[]
  eventBreaks: EventBreak[]
  filteredTodayEvents: StationEvent[]
  filteredUpcoming: UpcomingEventOccurrence[]
  overviewFields: Record<number, EventField[]>
  myRegistrations: EventRegistrationEntry[]
  managedMembersCount: number
  registering: boolean
  hasMore: boolean
  loadingMore: boolean
  multiDayEndDate: (event: StationEvent, date: string) => string | null
  getRegistrationSummary: (eventId: number, date: string) => {accepted: number; pending: number; declined: number; total: number}
  getEligibleMembers: (eventId: number) => AnswerablePerson[]
  todayDetailRoute: (event: StationEvent) => RouteLocationRaw
  eventDetailRoute: (event: StationEvent, date: string) => RouteLocationRaw
  formatTime: (iso: string | null | undefined) => string
  formatDeadline: (iso: string) => string
}>()

defineEmits<{
  (e: 'update:view-mode', value: ViewMode): void
  (e: 'update:search', value: string): void
  (e: 'update:category-id', value: string): void
  (e: 'update:needs-action', value: boolean): void
  (e: 'create'): void
  (e: 'attendance', event: StationEvent): void
  (e: 'register', event: StationEvent, date: string, people: AnswerablePerson[]): void
  (e: 'decline', event: StationEvent, date: string, people: AnswerablePerson[]): void
  (e: 'withdraw', regId: number): void
  (e: 'load-more'): void
}>()
</script>

<template>
  <UpcomingHeaderBar
      :view-mode="viewMode"
      :can-create="canCreate"
      @update:view-mode="$emit('update:view-mode', $event)"
      @create="$emit('create')"
  />

  <EventFilterBar
      :search="searchQuery"
      :category-id="selectedCategoryId"
      :needs-action="showNeedsAction"
      :categories="categories"
      @update:search="$emit('update:search', $event)"
      @update:category-id="$emit('update:category-id', $event)"
      @update:needs-action="$emit('update:needs-action', $event)"
  />

  <EventsCalendar
      v-if="viewMode === 'calendar'"
      :all-events="allEvents"
      :event-breaks="eventBreaks"
      :selected-category-id="selectedCategoryId"
      :search-query="searchQuery"
      :categories="categories"
  />

  <template v-if="viewMode === 'list'">
    <TodayEventsSection
        :events="filteredTodayEvents"
        :overview-fields="overviewFields"
        :can-manage-attendance="canManageAttendance"
        :detail-route="todayDetailRoute"
        :format-time="formatTime"
        @attendance="$emit('attendance', $event)"
    />

    <FederatedEventsSection/>

    <UpcomingEventsSection
        :items="filteredUpcoming"
        :categories="categories"
        :overview-fields="overviewFields"
        :my-registrations="myRegistrations"
        :managed-members-count="managedMembersCount"
        :registering="registering"
        :has-more="hasMore"
        :loading-more="loadingMore"
        :multi-day-end-date="multiDayEndDate"
        :get-registration-summary="getRegistrationSummary"
        :get-eligible-members="getEligibleMembers"
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

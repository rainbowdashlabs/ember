/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import UpcomingEventItem from './UpcomingEventItem.vue'
import type {AnswerablePerson} from '@/util/eventAnswers'
import type {PagedListView} from '@/composables/usePagedList'
import type {
  EventCategory,
  EventField,
  EventRegistrationEntry,
  StationEvent,
  UpcomingEventOccurrence,
} from '@/api/events'

/**
 * A run of dates, in the order the server put them, with the button that asks for the next page.
 *
 * <p>The same section serves both tabs. A date that has passed takes no answer, so the controls for
 * giving one are absent there, and nothing else about a row differs.
 */
defineProps<{
  title: string
  emptyMessage: string
  list: PagedListView<UpcomingEventOccurrence>
  answerable: boolean
  categories: EventCategory[]
  overviewFields: Record<number, EventField[]>
  myRegistrations: EventRegistrationEntry[]
  managedMembersCount: number
  registering: boolean
  multiDayEndDate: (event: StationEvent, startDate: string) => string | null
  getRegistrationSummary: (eventId: number, date: string) => { accepted: number; pending: number; declined: number; total: number }
  getEligibleMembers: (eventId: number) => AnswerablePerson[]
  getRestrictionNote: (eventId: number) => string | null
  detailRoute: (event: StationEvent, date: string) => RouteLocationRaw
  formatTime: (iso?: string) => string
  formatDeadline: (iso: string) => string
}>()

const emit = defineEmits<{
  register: [event: StationEvent, date: string, people: AnswerablePerson[]]
  decline: [event: StationEvent, date: string, people: AnswerablePerson[]]
  withdraw: [registrationId: number]
  loadMore: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ title }}</SubHeader>
    <EmptyState v-if="!list.items.length" compact>{{ emptyMessage }}</EmptyState>
    <div v-else class="space-y-2">
      <UpcomingEventItem
          v-for="item in list.items"
          :key="`${item.event.id}-${item.date}`"
          :event="item.event"
          :date="item.date"
          :end-date="multiDayEndDate(item.event, item.date)"
          :category="categories.find(c => c.id === item.event.categoryId)"
          :overview-fields="overviewFields[item.event.id] ?? []"
          :registration-summary="getRegistrationSummary(item.event.id, item.date)"
          :detail-route="detailRoute(item.event, item.date)"
          :eligible-members="getEligibleMembers(item.event.id)"
          :restriction-note="getRestrictionNote(item.event.id)"
          :registrations="myRegistrations.filter(r => r.eventId === item.event.id && r.eventDate === item.date)"
          :has-managed-members="managedMembersCount > 0"
          :registering="registering"
          :answerable="answerable"
          :format-time="formatTime"
          :format-deadline="formatDeadline"
          @register="emit('register', item.event, item.date, $event)"
          @decline="emit('decline', item.event, item.date, $event)"
          @withdraw="emit('withdraw', $event)"
      />
    </div>
    <div v-if="list.hasMore" class="flex justify-center pt-2">
      <SecondaryButton :disabled="list.loadingMore" @click="emit('loadMore')">
        <Spinner v-if="list.loadingMore" size="sm" class="mr-2"/>
        {{ t('eventsUpcoming.loadMore') }}
      </SecondaryButton>
    </div>
  </div>
</template>

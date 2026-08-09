/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import UpcomingEventItem from './UpcomingEventItem.vue'
import type {EventField, EventRegistrationEntry, StationEvent, UpcomingEventOccurrence} from '@/api/events'

defineProps<{
  items: UpcomingEventOccurrence[]
  overviewFields: Record<number, EventField[]>
  myRegistrations: EventRegistrationEntry[]
  managedMembersCount: number
  registering: boolean
  hasMore: boolean
  loadingMore: boolean
  multiDayEndDate: (event: StationEvent, startDate: string) => string | null
  getRegistrationSummary: (eventId: number, date: string) => { accepted: number; pending: number; declined: number; total: number }
  getEligibleMembers: (eventId: number) => { id: number; name: string }[]
  detailRoute: (event: StationEvent, date: string) => RouteLocationRaw
  dayLabel: (date: string) => string
  formatTime: (iso?: string) => string
  formatDeadline: (iso: string) => string
}>()

const emit = defineEmits<{
  register: [event: StationEvent, date: string, memberId: number]
  decline: [event: StationEvent, date: string, memberId: number]
  withdraw: [registrationId: number]
  loadMore: []
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="items.length > 0" class="space-y-3">
    <SubHeader>{{ t('eventsUpcoming.upcoming') }}</SubHeader>
    <div class="space-y-2">
      <UpcomingEventItem
          v-for="item in items"
          :key="`${item.event.id}-${item.date}`"
          :event="item.event"
          :date="item.date"
          :end-date="multiDayEndDate(item.event, item.date)"
          :overview-fields="overviewFields[item.event.id] ?? []"
          :registration-summary="getRegistrationSummary(item.event.id, item.date)"
          :detail-route="detailRoute(item.event, item.date)"
          :eligible-members="getEligibleMembers(item.event.id)"
          :registrations="myRegistrations.filter(r => r.eventId === item.event.id && r.eventDate === item.date)"
          :has-managed-members="managedMembersCount > 0"
          :registering="registering"
          :day-label="dayLabel"
          :format-time="formatTime"
          :format-deadline="formatDeadline"
          @register="emit('register', item.event, item.date, $event)"
          @decline="emit('decline', item.event, item.date, $event)"
          @withdraw="emit('withdraw', $event)"
      />
    </div>
    <div v-if="hasMore" class="flex justify-center pt-2">
      <SecondaryButton :disabled="loadingMore" @click="emit('loadMore')">
        <Spinner v-if="loadingMore" size="sm" class="mr-2"/>
        {{ t('eventsUpcoming.loadMore') }}
      </SecondaryButton>
    </div>
  </div>
</template>

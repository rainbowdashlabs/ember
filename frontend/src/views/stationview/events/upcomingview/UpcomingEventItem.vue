/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import EventFieldValue from '../eventshared/EventFieldValue.vue'
import EventRegistrationActions from './EventRegistrationActions.vue'
import type {EventField, EventRegistrationEntry, StationEvent} from '@/api/events'

const props = defineProps<{
  event: StationEvent
  date: string
  endDate: string | null
  overviewFields: EventField[]
  registrationSummary: { accepted: number; pending: number; declined: number; total: number }
  detailRoute: RouteLocationRaw
  eligibleMembers: { id: number; name: string }[]
  registrations: EventRegistrationEntry[]
  hasManagedMembers: boolean
  registering: boolean
  dayLabel: (date: string) => string
  formatTime: (iso?: string) => string
  formatDeadline: (iso: string) => string
}>()

const emit = defineEmits<{
  register: [memberId: number]
  decline: [memberId: number]
  withdraw: [registrationId: number]
}>()

const {t} = useI18n()

const containerClass = computed(() => [
  'space-y-2',
  props.endDate ? 'border-l-4 border-(--accent)' : '',
])
</script>

<template>
  <NeutralContainer :class="containerClass">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div>
        <span v-if="endDate" :title="t('eventsUpcoming.multiDay')" class="mr-1 inline-block">
          <MutedIcon :icon="['fas', 'calendar-days']"/>
        </span>
        <router-link :to="detailRoute" class="font-medium text-primary hover:underline">{{ event.name }}</router-link>
        <MutedIcon v-if="event.restricted" :icon="['fas', 'lock']" class="ml-1"/>
        <MutedText size="sm" class="ml-2">
          <template v-if="endDate">{{ dayLabel(date) }}, {{ date }} – {{ dayLabel(endDate) }}, {{ endDate }}</template>
          <template v-else>{{ dayLabel(date) }}, {{ date }}</template>
        </MutedText>
        <MutedText class="ml-2">{{ formatTime(event.startTime) }} – {{ formatTime(event.endTime) }}</MutedText>
        <MutedText v-if="event.requiresRegistration && event.registrationDeadline" class="ml-2 text-xs text-(--text-muted)">({{ t('eventsUpcoming.deadline') }}: {{ formatDeadline(event.registrationDeadline) }})</MutedText>
        <p v-if="event.description" class="text-sm text-(--text-muted) mt-0.5">{{ event.description }}</p>
        <div v-if="overviewFields.length" class="flex flex-wrap gap-3 text-xs mt-1">
          <span v-for="f in overviewFields" :key="f.id" class="text-(--text-muted)"><span class="font-medium">{{ f.name }}:</span> <EventFieldValue :field-type="f.fieldType" :value="f.value"/></span>
        </div>
      </div>
      <div v-if="registrationSummary.total > 0" class="flex items-center gap-2 text-xs">
        <SuccessBadge v-if="registrationSummary.accepted">{{ registrationSummary.accepted }} {{ t('eventsUpcoming.accepted') }}</SuccessBadge>
        <InfoBadge v-if="registrationSummary.pending">{{ registrationSummary.pending }} {{ t('eventsUpcoming.pendingCount') }}</InfoBadge>
        <ErrorBadge v-if="registrationSummary.declined">{{ registrationSummary.declined }} {{ t('eventsUpcoming.declinedCount') }}</ErrorBadge>
      </div>
    </div>
    <EventRegistrationActions
        :eligible-members="eligibleMembers"
        :registrations="registrations"
        :requires-registration="!!event.requiresRegistration"
        :has-managed-members="hasManagedMembers"
        :registering="registering"
        @register="emit('register', $event)"
        @decline="emit('decline', $event)"
        @withdraw="emit('withdraw', $event)"
    />
  </NeutralContainer>
</template>

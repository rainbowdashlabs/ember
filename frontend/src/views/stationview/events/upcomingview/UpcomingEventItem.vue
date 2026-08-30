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
import ColorBadge from '@/components/badge/ColorBadge.vue'
import EventHeadline from '../eventshared/EventHeadline.vue'
import EventFieldValue from '../eventshared/EventFieldValue.vue'
import EventRegistrationActions from '../eventshared/EventRegistrationActions.vue'
import type {EventCategory, EventField, EventRegistrationEntry, StationEvent} from '@/api/events'
import {markdownSnippet} from '@/util/markdown'
import {localAnswers, type AnswerablePerson} from '@/util/eventAnswers'

const props = defineProps<{
  event: StationEvent
  date: string
  endDate: string | null
  /** What kind of appointment this is, absent where it was put in no category. */
  category?: EventCategory | null
  overviewFields: EventField[]
  registrationSummary: { accepted: number; pending: number; declined: number; total: number }
  detailRoute: RouteLocationRaw
  eligibleMembers: AnswerablePerson[]
  registrations: EventRegistrationEntry[]
  hasManagedMembers: boolean
  registering: boolean
  formatTime: (iso?: string) => string
  formatDeadline: (iso: string) => string
}>()

const answers = computed(() => localAnswers(props.eligibleMembers, props.registrations))

const emit = defineEmits<{
  register: [people: AnswerablePerson[]]
  decline: [people: AnswerablePerson[]]
  withdraw: [registrationId: number]
}>()

const {t} = useI18n()

const containerClass = computed(() => [
  'space-y-2',
  props.endDate ? 'border-l-4 border-(--accent)' : '',
])
</script>

<template>
  <NeutralContainer data-testid="upcoming-event" :data-event="event.id" :data-date="date" :class="containerClass">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div>
        <EventHeadline
            :name="event.name" :to="detailRoute" :date="date" :end-date="endDate"
            :start-time="event.startTime" :end-time="event.endTime" :format-time="formatTime">
          <ColorBadge v-if="category" :color="category.color" data-testid="upcoming-event-category">
            {{ category.name }}
          </ColorBadge>
          <MutedIcon v-if="event.restricted" :icon="['fas', 'lock']"/>
          <InfoBadge v-if="event.requiresRegistration" data-testid="upcoming-event-registration">
            {{ t('eventsUpcoming.registrationRequired') }}
          </InfoBadge>
          <MutedText v-if="event.requiresRegistration && event.registrationDeadline" class="text-xs text-(--text-muted)">({{ t('eventsUpcoming.deadline') }}: {{ formatDeadline(event.registrationDeadline) }})</MutedText>
        </EventHeadline>
        <p v-if="event.description" class="text-sm text-(--text-muted) mt-0.5">
          {{ markdownSnippet(event.description) }}
        </p>
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
        :people="eligibleMembers"
        :answers="answers"
        :requires-registration="!!event.requiresRegistration"
        :registration-deadline="event.registrationDeadline"
        :has-managed-members="hasManagedMembers"
        :registering="registering"
        @register="emit('register', $event)"
        @decline="emit('decline', $event)"
        @withdraw="emit('withdraw', $event)"
    />
  </NeutralContainer>
</template>

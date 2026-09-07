/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import RegistrationStatusBadge from '@/views/stationview/events/eventshared/RegistrationStatusBadge.vue'
import type {EventCategory, EventRegistrationEntry, StationEvent} from '@/api/events'
import {formatDate, formatTime} from '@/util/format'

defineProps<{
  event: StationEvent
  date: string
  dayLabel: string
  /** What kind of appointment this is, absent where it was put in no category. */
  category?: EventCategory | null
  /** What the household has answered for this date, one entry per person. */
  answers: {registration: EventRegistrationEntry; name: string}[]
  /** How many people the reader answers for have not said anything yet. */
  openCount: number
  /** Whether to name who answered, which only helps somebody answering for others. */
  showNames: boolean
  busy: boolean
}>()

defineEmits<{
  open: []
  decline: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="py-2 px-3 cursor-pointer hover:bg-(--bg-accent)" @click="$emit('open')">
    <div class="flex items-start justify-between gap-2">
      <div class="min-w-0">
        <div class="flex items-center gap-2 min-w-0">
          <p class="truncate text-sm font-medium">{{ event.name }}</p>
          <ColorBadge v-if="category" :color="category.color" class="shrink-0" data-testid="dashboard-event-category">
            {{ category.name }}
          </ColorBadge>
        </div>
        <p class="text-xs text-(--text-muted)">
          {{ dayLabel }}, {{ formatDate(date + 'T00:00:00') }}
          <template v-if="event.startTime"> · {{ formatTime(event.startTime) }}</template>
          <template v-if="event.endTime"> – {{ formatTime(event.endTime) }}</template>
        </p>
      </div>

      <InfoBadge v-if="event.requiresRegistration && answers.length === 0" class="shrink-0">
        {{ t('dashboard.registrationRequired') }}
      </InfoBadge>
      <SecondaryButton
          v-else-if="!event.requiresRegistration && openCount > 0"
          :disabled="busy"
          :data-testid="`dashboard-decline-${event.id}`"
          class="shrink-0"
          compact
          @click.stop="$emit('decline')"
      >
        {{ t('eventsUpcoming.decline') }}
      </SecondaryButton>
    </div>

    <div v-if="answers.length > 0" class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
      <span v-for="answer in answers" :key="answer.registration.id" class="flex items-center gap-1">
        <span v-if="showNames" class="text-xs text-(--text-muted)">{{ answer.name }}</span>
        <RegistrationStatusBadge :status="answer.registration.status"/>
      </span>
    </div>
  </NeutralContainer>
</template>

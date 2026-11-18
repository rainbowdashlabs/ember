/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventSummary, RegistrationStatus} from '@/api/generated/schema'
import {formatWeekdayDate} from '@/util/format'
import {placesFromCounts, placesLabel} from '@/util/eventPlaces'

const props = defineProps<{
  event: EventSummary
  date: string
  counts: Record<RegistrationStatus, number>
  deadlineExpired: boolean
  formatDeadline: (iso?: string | null) => string
}>()

const {t} = useI18n()

/** How the places of the date stand, null where the appointment limits nothing. */
const places = computed(() => placesFromCounts(
    props.event.registrationLimit,
    Object.entries(props.counts).map(([status, count]) => ({status: status as RegistrationStatus, count}))))
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <div>
      <span class="font-medium text-primary">{{ event.name }}</span>
      <MutedText class="ml-2">{{ formatWeekdayDate(date, 'short') }}</MutedText>
      <MutedText v-if="event.registrationDeadline" class="ml-2">
        {{ t('eventsRegistrations.deadline') }}: {{ formatDeadline(event.registrationDeadline) }}
      </MutedText>
      <MutedText v-if="places" class="ml-2" data-testid="pending-date-places">
        ({{ placesLabel(places, t) }})
      </MutedText>
    </div>
    <div class="flex items-center gap-2 flex-wrap">
      <InfoBadge>
        <font-awesome-icon :icon="['fas', 'hourglass-half']" class="mr-1"/>
        {{ counts.PENDING }} {{ t('eventsRegistrations.pending') }}
      </InfoBadge>
      <SuccessBadge v-if="counts.ACCEPTED > 0">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ counts.ACCEPTED }} {{ t('eventsRegistrations.accepted') }}
      </SuccessBadge>
      <ErrorBadge v-if="counts.DENIED > 0">
        <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
        {{ counts.DENIED }} {{ t('eventsRegistrations.denied') }}
      </ErrorBadge>
      <SecondaryBadge v-if="counts.QUEUED > 0">
        <font-awesome-icon :icon="['fas', 'list-ol']" class="mr-1"/>
        {{ t('registrationQueue.waitingCount', {count: counts.QUEUED}) }}
      </SecondaryBadge>
      <SecondaryBadge v-if="counts.DECLINED > 0">
        <font-awesome-icon :icon="['fas', 'user-slash']" class="mr-1"/>
        {{ counts.DECLINED }} {{ t('eventsRegistrations.declined') }}
      </SecondaryBadge>
      <SecondaryBadge v-if="counts.WITHDRAWN > 0">
        <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1"/>
        {{ counts.WITHDRAWN }} {{ t('eventsRegistrations.withdrawn') }}
      </SecondaryBadge>
      <ErrorBadge v-if="deadlineExpired">{{ t('eventsRegistrations.expired') }}</ErrorBadge>
    </div>
  </div>
</template>

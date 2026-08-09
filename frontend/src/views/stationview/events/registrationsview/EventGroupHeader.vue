/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {StationEvent} from '@/api/events'

defineProps<{
  event: StationEvent
  counts: { PENDING: number; ACCEPTED: number; DENIED: number; DECLINED: number; WITHDRAWN: number }
  deadlineExpired: boolean
  formatDeadline: (iso?: string | null) => string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-2">
    <div>
      <span class="font-medium text-primary">{{ event.name }}</span>
      <MutedText v-if="event.registrationDeadline" class="ml-2">
        {{ t('eventsRegistrations.deadline') }}: {{ formatDeadline(event.registrationDeadline) }}
      </MutedText>
      <MutedText v-if="event.registrationLimit" class="ml-2">
        ({{ t('eventsRegistrations.limit') }}: {{ event.registrationLimit }})
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

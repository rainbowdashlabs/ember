/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import EventGroupHeader from './EventGroupHeader.vue'
import StatusSections from './StatusSections.vue'
import type {RegistrationStatusName} from '@/api/events'
import type {EventSummary, RegistrationResponse, RegistrationStatsResponse} from '@/api/generated/schema'

defineProps<{
  event: EventSummary
  counts: Record<RegistrationStatusName, number>
  deadlineExpired: boolean
  expanded: boolean
  expandedLoading: boolean
  expandedByStatus: Record<RegistrationStatusName, RegistrationResponse[]>
  registrationStats: RegistrationStatsResponse[]
  formatDeadline: (iso?: string | null) => string
}>()

const emit = defineEmits<{
  toggle: []
  accept: [regId: number]
  deny: [regId: number]
}>()
</script>

<template>
  <component
      :is="deadlineExpired ? ErrorContainer : NeutralContainer"
      class="space-y-3 cursor-pointer"
      @click="emit('toggle')"
  >
    <EventGroupHeader
        :event="event"
        :counts="counts"
        :deadline-expired="deadlineExpired"
        :format-deadline="formatDeadline"
    />
    <StatusSections
        v-if="expanded"
        :loading="expandedLoading"
        :by-status="expandedByStatus"
        :stats="registrationStats"
        @accept="(id) => emit('accept', id)"
        @deny="(id) => emit('deny', id)"
    />
  </component>
</template>

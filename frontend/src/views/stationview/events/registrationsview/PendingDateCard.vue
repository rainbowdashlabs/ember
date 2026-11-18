/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import PendingDateHeader from './PendingDateHeader.vue'
import StatusSections from './StatusSections.vue'
import type {EventSummary, RegistrationResponse, RegistrationStatsResponse, RegistrationStatus} from '@/api/generated/schema'
import type {QueueEntry} from '@/util/registrationQueue'

defineProps<{
  event: EventSummary
  date: string
  counts: Record<RegistrationStatus, number>
  deadlineExpired: boolean
  expanded: boolean
  expandedLoading: boolean
  expandedByStatus: Record<RegistrationStatus, RegistrationResponse[]>
  /** Everybody waiting for a place on the opened date, first in line first. */
  expandedQueue: QueueEntry[]
  /** Whether a partner decides about its own people, whose rows on the waiting list then offer nothing. */
  partnerDecides: (partnerId: number) => boolean
  /** Whether the date keeps a waiting list, which only an appointment with a participant limit does. */
  canQueue: boolean
  busy: boolean
  registrationStats: RegistrationStatsResponse[]
  formatDeadline: (iso?: string | null) => string
}>()

const emit = defineEmits<{
  toggle: []
  accept: [regId: number]
  deny: [regId: number]
  queue: [regIds: number[]]
  reorder: [entry: QueueEntry, position: number]
  moveUp: [entry: QueueEntry]
}>()
</script>

<template>
  <component
      :is="deadlineExpired ? ErrorContainer : NeutralContainer"
      class="space-y-3 cursor-pointer"
      @click="emit('toggle')"
  >
    <PendingDateHeader
        :event="event"
        :date="date"
        :counts="counts"
        :deadline-expired="deadlineExpired"
        :format-deadline="formatDeadline"
    />
    <StatusSections
        v-if="expanded"
        :loading="expandedLoading"
        :by-status="expandedByStatus"
        :queue="expandedQueue"
        :partner-decides="partnerDecides"
        :can-queue="canQueue"
        :busy="busy"
        :stats="registrationStats"
        @accept="(id) => emit('accept', id)"
        @deny="(id) => emit('deny', id)"
        @queue="(ids) => emit('queue', ids)"
        @reorder="(entry, position) => emit('reorder', entry, position)"
        @move-up="(entry) => emit('moveUp', entry)"
    />
  </component>
</template>

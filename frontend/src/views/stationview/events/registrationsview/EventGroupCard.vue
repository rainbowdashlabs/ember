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
import type {StationEvent} from '@/api/types'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'

type StatusKey = 'PENDING' | 'ACCEPTED' | 'DENIED' | 'DECLINED' | 'WITHDRAWN'

defineProps<{
  event: StationEvent
  counts: { PENDING: number; ACCEPTED: number; DENIED: number; DECLINED: number; WITHDRAWN: number }
  deadlineExpired: boolean
  expanded: boolean
  expandedLoading: boolean
  expandedByStatus: Record<StatusKey, EventRegistrationEntry[]>
  registrationStats: MemberRegistrationStats[]
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

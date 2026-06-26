/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EventGroupCard from './registrationsview/EventGroupCard.vue'
import {events} from '@/api'
import type {StationEvent} from '@/api/types'
import {RegistrationStatus} from '@/api/types'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useEventEditDeps} from '@/composables/useEventEditDeps'

const {t} = useI18n()

const {registrationCounts, reload: reloadDeps} = useEventEditDeps({withMembers: true, withCounts: true, autoLoad: false})
const pendingRegistrations = ref<EventRegistrationEntry[]>([])
const allEvents = ref<StationEvent[]>([])
const expandedEventId = ref<number | null>(null)
const registrationStats = ref<MemberRegistrationStats[]>([])
const expandedRegistrations = ref<EventRegistrationEntry[]>([])
const expandedLoading = ref(false)

type StatusKey = 'PENDING' | 'ACCEPTED' | 'DENIED' | 'DECLINED' | 'WITHDRAWN'

interface StatusCounts {
  PENDING: number
  ACCEPTED: number
  DENIED: number
  DECLINED: number
  WITHDRAWN: number
}

interface EventGroup {
  event: StationEvent
  pending: EventRegistrationEntry[]
  counts: StatusCounts
  deadlineExpired: boolean
}

function emptyCounts(): StatusCounts {
  return {PENDING: 0, ACCEPTED: 0, DENIED: 0, DECLINED: 0, WITHDRAWN: 0}
}

const eventGroups = computed((): EventGroup[] => {
  const pendingByEvent = new Map<number, EventRegistrationEntry[]>()
  for (const reg of pendingRegistrations.value) {
    const list = pendingByEvent.get(reg.eventId) ?? []
    list.push(reg)
    pendingByEvent.set(reg.eventId, list)
  }

  // Aggregate counts per event across all dates from listRegistrationCounts.
  const countsByEvent = new Map<number, StatusCounts>()
  for (const rc of registrationCounts.value) {
    const cur = countsByEvent.get(rc.eventId) ?? emptyCounts()
    const key = rc.status as StatusKey
    if (key in cur) {
      cur[key] += rc.count
    }
    countsByEvent.set(rc.eventId, cur)
  }

  const result: EventGroup[] = []
  for (const [eventId, regs] of pendingByEvent) {
    const event = allEvents.value.find(e => e.id === eventId)
    if (!event) continue
    const counts = countsByEvent.get(eventId) ?? emptyCounts()
    // Ensure the pending list size is reflected even if counts endpoint lagged.
    if (counts.PENDING < regs.length) counts.PENDING = regs.length
    const deadlineExpired = event.registrationDeadline
        ? new Date(event.registrationDeadline) < new Date()
        : false
    result.push({event, pending: regs, counts, deadlineExpired})
  }

  // Sort by deadline ascending (null = end)
  result.sort((a, b) => {
    const da = a.event.registrationDeadline ?? '9999'
    const db = b.event.registrationDeadline ?? '9999'
    return da.localeCompare(db)
  })
  return result
})

function formatDeadline(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'})
}

const expandedByStatus = computed(() => {
  const groups: Record<StatusKey, EventRegistrationEntry[]> = {
    PENDING: [],
    ACCEPTED: [],
    DENIED: [],
    DECLINED: [],
    WITHDRAWN: [],
  }
  for (const reg of expandedRegistrations.value) {
    const key = reg.status as StatusKey
    if (key in groups) {
      groups[key].push(reg)
    }
  }
  return groups
})

async function toggleExpand(eventId: number) {
  if (expandedEventId.value === eventId) {
    expandedEventId.value = null
    expandedRegistrations.value = []
    return
  }
  expandedEventId.value = eventId
  expandedLoading.value = true
  try {
    const [stats, regs] = await Promise.all([
      events.getRegistrationStats(eventId),
      events.listEventRegistrations(eventId),
    ])
    registrationStats.value = stats
    expandedRegistrations.value = regs
  } catch { /* ignore */ }
  finally { expandedLoading.value = false }
}

async function refreshExpanded() {
  const id = expandedEventId.value
  if (id == null) return
  try {
    const [regs, counts] = await Promise.all([
      events.listEventRegistrations(id),
      events.listRegistrationCounts(),
    ])
    expandedRegistrations.value = regs
    registrationCounts.value = counts
  } catch {
    /* ignore */
  }
}

async function accept(regId: number) {
  error.value = ''
  try {
    await events.updateRegistrationStatus(regId, RegistrationStatus.ACCEPTED)
    pendingRegistrations.value = pendingRegistrations.value.filter(r => r.id !== regId)
    await refreshExpanded()
  } catch { error.value = t('common.error') }
}

async function deny(regId: number) {
  error.value = ''
  try {
    await events.updateRegistrationStatus(regId, RegistrationStatus.DENIED)
    pendingRegistrations.value = pendingRegistrations.value.filter(r => r.id !== regId)
    await refreshExpanded()
  } catch { error.value = t('common.error') }
}

const {loading, error} = useAsyncLoader(async () => {
  const [regs, evs] = await Promise.all([
    events.listPendingRegistrations(),
    events.listEvents(),
    reloadDeps(),
  ])
  pendingRegistrations.value = regs
  allEvents.value = evs
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('eventsRegistrations.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="eventGroups.length === 0">{{ t('eventsRegistrations.empty') }}</EmptyState>
        <div class="space-y-3">
          <EventGroupCard
              v-for="group in eventGroups"
              :key="group.event.id"
              :event="group.event"
              :counts="group.counts"
              :deadline-expired="group.deadlineExpired"
              :expanded="expandedEventId === group.event.id"
              :expanded-loading="expandedLoading"
              :expanded-by-status="expandedByStatus"
              :registration-stats="registrationStats"
              :format-deadline="formatDeadline"
              @toggle="toggleExpand(group.event.id)"
              @accept="accept"
              @deny="deny"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>

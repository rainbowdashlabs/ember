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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import EventGroupCard from './registrationsview/EventGroupCard.vue'
import {events} from '@/api'
import {RegistrationStatus, type RegistrationStatusName} from '@/api/events'
import type {EventSummary, RegistrationResponse, RegistrationStatsResponse} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useEventEditDeps} from '@/composables/useEventEditDeps'
import {formatDateTime} from '@/util/format'
import {describeFailure} from '@/util/failure'

const {t} = useI18n()

const {registrationCounts, reload: reloadDeps} = useEventEditDeps({withMembers: true, withCounts: true, autoLoad: false})
const pendingRegistrations = ref<RegistrationResponse[]>([])
const allEvents = ref<EventSummary[]>([])
const expandedEventId = ref<number | null>(null)
const registrationStats = ref<RegistrationStatsResponse[]>([])
const expandedRegistrations = ref<RegistrationResponse[]>([])
const expandedLoading = ref(false)

type StatusCounts = Record<RegistrationStatusName, number>

interface EventGroup {
  event: EventSummary
  pending: RegistrationResponse[]
  counts: StatusCounts
  deadlineExpired: boolean
}

function emptyCounts(): StatusCounts {
  return {PENDING: 0, ACCEPTED: 0, DENIED: 0, DECLINED: 0, WITHDRAWN: 0}
}

/**
 * The appointments with pending registrations, soonest deadline first. The pending count never
 * falls below the pending list, in case the counts lagged behind it.
 */
const eventGroups = computed((): EventGroup[] => {
  const pendingByEvent = new Map<number, RegistrationResponse[]>()
  for (const reg of pendingRegistrations.value) {
    const list = pendingByEvent.get(reg.eventId) ?? []
    list.push(reg)
    pendingByEvent.set(reg.eventId, list)
  }

  const countsByEvent = new Map<number, StatusCounts>()
  for (const rc of registrationCounts.value) {
    const cur = countsByEvent.get(rc.eventId) ?? emptyCounts()
    cur[rc.status] += rc.count
    countsByEvent.set(rc.eventId, cur)
  }

  const result: EventGroup[] = []
  for (const [eventId, regs] of pendingByEvent) {
    const event = allEvents.value.find(e => e.id === eventId)
    if (!event) continue
    const counts = countsByEvent.get(eventId) ?? emptyCounts()
    if (counts.PENDING < regs.length) counts.PENDING = regs.length
    const deadlineExpired = event.registrationDeadline
        ? new Date(event.registrationDeadline) < new Date()
        : false
    result.push({event, pending: regs, counts, deadlineExpired})
  }

  result.sort((a, b) => {
    const da = a.event.registrationDeadline ?? '9999'
    const db = b.event.registrationDeadline ?? '9999'
    return da.localeCompare(db)
  })
  return result
})

const expandedByStatus = computed(() => {
  const groups: Record<RegistrationStatusName, RegistrationResponse[]> = {
    PENDING: [],
    ACCEPTED: [],
    DENIED: [],
    DECLINED: [],
    WITHDRAWN: [],
  }
  for (const reg of expandedRegistrations.value) {
    groups[reg.status].push(reg)
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
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
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
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

/**
 * Answers one sign-up, then reads the event back.
 *
 * <p>The two are answered for separately. Reading back is a refresh, and a refusal that was written
 * followed by a refresh that failed used to read as a refusal that was not written: the row was gone
 * from the list and the page said it had not worked.
 */
async function decide(regId: number, status: RegistrationStatusName) {
  failure.value = null
  try {
    await events.updateRegistrationStatus(regId, status)
    pendingRegistrations.value = pendingRegistrations.value.filter(r => r.id !== regId)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await refreshExpanded()
}

const {loading, failure} = useAsyncLoader(async () => {
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
  <ViewContent
      :title="t('pages.events-registrations.title')"
      :subtitle="t('pages.events-registrations.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

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
              :format-deadline="formatDateTime"
              @toggle="toggleExpand(group.event.id)"
              @accept="id => decide(id, RegistrationStatus.ACCEPTED)"
              @deny="id => decide(id, RegistrationStatus.DENIED)"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>

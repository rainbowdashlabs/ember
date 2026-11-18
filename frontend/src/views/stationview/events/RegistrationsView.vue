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
import PendingDateCard from './registrationsview/PendingDateCard.vue'
import QueueDialogs from './eventshared/QueueDialogs.vue'
import {appointmentStatuses, useQueueActions} from './eventshared/useQueueActions'
import {appointmentQueueRoutes} from './eventshared/queueRoutes'
import {events} from '@/api'
import type {
  RegistrationStatus,
  EnrichedFederationRegistration,
  PartnerPlacesView,
  EventSummary,
  RegistrationResponse,
  RegistrationStatsResponse,
} from '@/api/generated/schema'
import {useDatePlaces} from './eventshared/useDatePlaces'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useEventEditDeps} from '@/composables/useEventEditDeps'
import {formatDateTime} from '@/util/format'
import {describeFailure} from '@/util/failure'

const {t} = useI18n()

const {registrationCounts, reload: reloadDeps} = useEventEditDeps({withMembers: true, withCounts: true, autoLoad: false})
const pendingRegistrations = ref<RegistrationResponse[]>([])
const allEvents = ref<EventSummary[]>([])
const expandedKey = ref<string | null>(null)
const registrationStats = ref<RegistrationStatsResponse[]>([])
const expandedRegistrations = ref<RegistrationResponse[]>([])
const expandedPartners = ref<EnrichedFederationRegistration[]>([])
const expandedPartnerPlaces = ref<PartnerPlacesView[]>([])
const expandedLoading = ref(false)

type StatusCounts = Record<RegistrationStatus, number>

interface PendingDate {
  key: string
  event: EventSummary
  date: string
  pending: RegistrationResponse[]
  counts: StatusCounts
  deadlineExpired: boolean
}

function emptyCounts(): StatusCounts {
  return {PENDING: 0, ACCEPTED: 0, DENIED: 0, DECLINED: 0, WITHDRAWN: 0, QUEUED: 0}
}

/** One block per date of an appointment, so each date of a recurring one is answered on its own. */
function groupKey(eventId: number, date: string): string {
  return `${eventId}:${date}`
}

/**
 * The dates of appointments with pending registrations, soonest deadline first and then by date. The
 * pending count never falls below the pending list, in case the counts lagged behind it.
 */
const pendingDates = computed((): PendingDate[] => {
  const pendingByDate = new Map<string, RegistrationResponse[]>()
  for (const reg of pendingRegistrations.value) {
    const key = groupKey(reg.eventId, reg.eventDate)
    const list = pendingByDate.get(key) ?? []
    list.push(reg)
    pendingByDate.set(key, list)
  }

  const countsByDate = new Map<string, StatusCounts>()
  for (const rc of registrationCounts.value) {
    const key = groupKey(rc.eventId, rc.eventDate)
    const cur = countsByDate.get(key) ?? emptyCounts()
    cur[rc.status] += rc.count
    countsByDate.set(key, cur)
  }

  const result: PendingDate[] = []
  for (const [key, regs] of pendingByDate) {
    const first = regs[0]
    const event = first && allEvents.value.find(e => e.id === first.eventId)
    if (!first || !event) continue
    const counts = countsByDate.get(key) ?? emptyCounts()
    if (counts.PENDING < regs.length) counts.PENDING = regs.length
    const deadlineExpired = event.registrationDeadline
        ? new Date(event.registrationDeadline) < new Date()
        : false
    result.push({key, event, date: first.eventDate, pending: regs, counts, deadlineExpired})
  }

  result.sort((a, b) => {
    const da = a.event.registrationDeadline ?? '9999'
    const db = b.event.registrationDeadline ?? '9999'
    return da.localeCompare(db) || a.date.localeCompare(b.date)
  })
  return result
})

const expandedByStatus = computed(() => {
  const groups: Record<RegistrationStatus, RegistrationResponse[]> = {
    PENDING: [],
    ACCEPTED: [],
    DENIED: [],
    DECLINED: [],
    WITHDRAWN: [],
    QUEUED: [],
  }
  for (const reg of expandedRegistrations.value) {
    groups[reg.status].push(reg)
  }
  return groups
})

const expandedGroup = computed(() => pendingDates.value.find(g => g.key === expandedKey.value) ?? null)

/**
 * How the places and the waiting list of the opened date stand, members of the station and partners'
 * members alike, and which partners decide about their own people.
 */
const expanded = useDatePlaces({
  limit: () => expandedGroup.value?.event.registrationLimit,
  registrations: () => expandedRegistrations.value,
  partners: () => expandedPartners.value,
  partnerPlaces: () => expandedPartnerPlaces.value,
})

async function toggleExpand(group: PendingDate) {
  if (expandedKey.value === group.key) {
    expandedKey.value = null
    expandedRegistrations.value = []
    return
  }
  expandedKey.value = group.key
  expandedRegistrations.value = []
  expandedPartners.value = []
  expandedPartnerPlaces.value = []
  registrationStats.value = []
  expandedLoading.value = true
  try {
    const [stats, regs, partners, partnerPlaces] = await Promise.all([
      events.getRegistrationStats(group.event.id),
      events.listEventRegistrations(group.event.id, group.date),
      events.listFederationRegistrations(group.event.id, group.date),
      events.getPartnerPlaces(group.event.id),
    ])
    registrationStats.value = stats
    expandedRegistrations.value = regs
    expandedPartners.value = partners
    expandedPartnerPlaces.value = partnerPlaces
  } catch (e) {
    expandedKey.value = null
    failure.value = describeFailure(e, t)
  }
  finally { expandedLoading.value = false }
}

/**
 * Reads the opened date back after an action, and with it which sign-ups still wait for a decision.
 * A failure is left for the caller, which says it as the list being stale.
 */
async function refreshExpanded() {
  const group = expandedGroup.value
  if (!group) return
  const [regs, partners, counts, pending] = await Promise.all([
    events.listEventRegistrations(group.event.id, group.date),
    events.listFederationRegistrations(group.event.id, group.date),
    events.listRegistrationCounts(),
    events.listPendingRegistrations(),
  ])
  expandedRegistrations.value = regs
  expandedPartners.value = partners
  registrationCounts.value = counts
  pendingRegistrations.value = pending
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

/**
 * Deciding the sign-ups of the opened date and keeping its waiting list, each followed by reading
 * the date back.
 *
 * <p>The two are answered for separately. Reading back is a refresh, and a refusal that was written
 * followed by a refresh that failed used to read as a refusal that was not written: the row was gone
 * from the list and the page said it had not worked.
 */
const queueActions = useQueueActions(
    () => expanded.places.value, refreshExpanded, failure, appointmentQueueRoutes,
    appointmentStatuses(() => expandedRegistrations.value, () => expandedPartners.value))
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
        <EmptyState v-if="pendingDates.length === 0">{{ t('eventsRegistrations.empty') }}</EmptyState>
        <div class="space-y-3">
          <PendingDateCard
              v-for="group in pendingDates"
              :key="group.key"
              :event="group.event"
              :date="group.date"
              :counts="group.counts"
              :deadline-expired="group.deadlineExpired"
              :expanded="expandedKey === group.key"
              :expanded-loading="expandedLoading"
              :expanded-by-status="expandedByStatus"
              :expanded-queue="expanded.queue.value"
              :partner-decides="expanded.partnerDecides"
              :can-queue="!!expanded.places.value"
              :busy="queueActions.busy.value"
              :registration-stats="registrationStats"
              :format-deadline="formatDateTime"
              @toggle="toggleExpand(group)"
              @accept="queueActions.accept"
              @deny="id => queueActions.deny(id)"
              @queue="queueActions.queue"
              @reorder="queueActions.reorder"
              @move-up="queueActions.moveUp"
          />
        </div>
        <QueueDialogs :actions="queueActions"/>
      </template>
    </div>
  </ViewContent>
</template>

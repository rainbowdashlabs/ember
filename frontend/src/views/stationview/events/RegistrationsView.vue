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
import {
  RegistrationStatus,
  type EventSummary,
  type RegistrationResponse,
  type RegistrationStatsResponse,
} from '@/api/generated/schema'
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
const expandedLoading = ref(false)

type StatusCounts = Record<RegistrationStatus, number>

interface EventGroup {
  key: string
  event: EventSummary
  date: string
  pending: RegistrationResponse[]
  counts: StatusCounts
  deadlineExpired: boolean
}

function emptyCounts(): StatusCounts {
  return {PENDING: 0, ACCEPTED: 0, DENIED: 0, DECLINED: 0, WITHDRAWN: 0}
}

/** One block per date of an appointment, so each date of a recurring one is answered on its own. */
function groupKey(eventId: number, date: string): string {
  return `${eventId}:${date}`
}

/**
 * The dates of appointments with pending registrations, soonest deadline first and then by date. The
 * pending count never falls below the pending list, in case the counts lagged behind it.
 */
const eventGroups = computed((): EventGroup[] => {
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

  const result: EventGroup[] = []
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
  }
  for (const reg of expandedRegistrations.value) {
    groups[reg.status].push(reg)
  }
  return groups
})

const expandedGroup = computed(() => eventGroups.value.find(g => g.key === expandedKey.value) ?? null)

async function toggleExpand(group: EventGroup) {
  if (expandedKey.value === group.key) {
    expandedKey.value = null
    expandedRegistrations.value = []
    return
  }
  expandedKey.value = group.key
  expandedLoading.value = true
  try {
    const [stats, regs] = await Promise.all([
      events.getRegistrationStats(group.event.id),
      events.listEventRegistrations(group.event.id, group.date),
    ])
    registrationStats.value = stats
    expandedRegistrations.value = regs
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
  finally { expandedLoading.value = false }
}

async function refreshExpanded(group: EventGroup | null) {
  if (!group) return
  try {
    const [regs, counts] = await Promise.all([
      events.listEventRegistrations(group.event.id, group.date),
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
async function decide(regId: number, status: RegistrationStatus) {
  failure.value = null
  const group = expandedGroup.value
  try {
    await events.updateRegistrationStatus(regId, status)
    pendingRegistrations.value = pendingRegistrations.value.filter(r => r.id !== regId)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await refreshExpanded(group)
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
              :key="group.key"
              :event="group.event"
              :date="group.date"
              :counts="group.counts"
              :deadline-expired="group.deadlineExpired"
              :expanded="expandedKey === group.key"
              :expanded-loading="expandedLoading"
              :expanded-by-status="expandedByStatus"
              :registration-stats="registrationStats"
              :format-deadline="formatDateTime"
              @toggle="toggleExpand(group)"
              @accept="id => decide(id, RegistrationStatus.ACCEPTED)"
              @deny="id => decide(id, RegistrationStatus.DENIED)"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>

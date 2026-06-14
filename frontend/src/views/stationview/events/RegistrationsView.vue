/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import RegistrationStatsTable from './eventdetailview/RegistrationStatsTable.vue'
import {events, stationMembers} from '@/api'
import type {StationEvent, StationMember} from '@/api/types'
import {RegistrationStatus} from '@/api/types'
import type {EventRegistrationEntry, MemberRegistrationStats, RegistrationCount} from '@/api/events'

const {t} = useI18n()

const pendingRegistrations = ref<EventRegistrationEntry[]>([])
const allEvents = ref<StationEvent[]>([])
const allMembers = ref<StationMember[]>([])
const registrationCounts = ref<RegistrationCount[]>([])
const loading = ref(true)
const error = ref('')
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
  } catch { /* ignore */ }
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [regs, evs, members, counts] = await Promise.all([
      events.listPendingRegistrations(),
      events.listEvents(),
      stationMembers.listMembers(),
      events.listRegistrationCounts(),
    ])
    pendingRegistrations.value = regs
    allEvents.value = evs
    allMembers.value = members
    registrationCounts.value = counts
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

onMounted(loadData)
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
          <component
            :is="group.deadlineExpired ? ErrorContainer : NeutralContainer"
            v-for="group in eventGroups"
            :key="group.event.id"
            class="space-y-3 cursor-pointer"
            @click="toggleExpand(group.event.id)"
          >
            <!-- Event summary row -->
            <div class="flex items-center justify-between flex-wrap gap-2">
              <div>
                <span class="font-medium text-primary">{{ group.event.name }}</span>
                <span v-if="group.event.registrationDeadline" class="text-xs text-(--text-muted) ml-2">
                  {{ t('eventsRegistrations.deadline') }}: {{ formatDeadline(group.event.registrationDeadline) }}
                </span>
                <span v-if="group.event.registrationLimit" class="text-xs text-(--text-muted) ml-2">
                  ({{ t('eventsRegistrations.limit') }}: {{ group.event.registrationLimit }})
                </span>
              </div>
              <div class="flex items-center gap-2 flex-wrap">
                <InfoBadge>
                  <font-awesome-icon :icon="['fas', 'hourglass-half']" class="mr-1"/>
                  {{ group.counts.PENDING }} {{ t('eventsRegistrations.pending') }}
                </InfoBadge>
                <SuccessBadge v-if="group.counts.ACCEPTED > 0">
                  <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                  {{ group.counts.ACCEPTED }} {{ t('eventsRegistrations.accepted') }}
                </SuccessBadge>
                <ErrorBadge v-if="group.counts.DENIED > 0">
                  <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
                  {{ group.counts.DENIED }} {{ t('eventsRegistrations.denied') }}
                </ErrorBadge>
                <SecondaryBadge v-if="group.counts.DECLINED > 0">
                  <font-awesome-icon :icon="['fas', 'user-slash']" class="mr-1"/>
                  {{ group.counts.DECLINED }} {{ t('eventsRegistrations.declined') }}
                </SecondaryBadge>
                <SecondaryBadge v-if="group.counts.WITHDRAWN > 0">
                  <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1"/>
                  {{ group.counts.WITHDRAWN }} {{ t('eventsRegistrations.withdrawn') }}
                </SecondaryBadge>
                <ErrorBadge v-if="group.deadlineExpired">{{ t('eventsRegistrations.expired') }}</ErrorBadge>
              </div>
            </div>

            <!-- Expanded: per-status groups + fair acceptance table -->
            <template v-if="expandedEventId === group.event.id">
              <div class="border-t border-(--border) pt-3 space-y-4" @click.stop>
                <Spinner v-if="expandedLoading" size="md"/>
                <template v-else>
                  <!-- Pending: priority — show with fair acceptance table + actions -->
                  <section v-if="expandedByStatus.PENDING.length > 0" class="space-y-2">
                    <SubHeader>
                      <font-awesome-icon :icon="['fas', 'hourglass-half']" class="mr-1"/>
                      {{ t('eventsRegistrations.groupPending') }} ({{ expandedByStatus.PENDING.length }})
                    </SubHeader>
                    <RegistrationStatsTable
                        :registrations="expandedByStatus.PENDING"
                        :stats="registrationStats"
                        :show-actions="true"
                        @accept="accept($event)"
                        @deny="deny($event)"
                    />
                  </section>

                  <!-- Accepted -->
                  <section v-if="expandedByStatus.ACCEPTED.length > 0" class="space-y-2">
                    <SubHeader>
                      <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                      {{ t('eventsRegistrations.groupAccepted') }} ({{ expandedByStatus.ACCEPTED.length }})
                    </SubHeader>
                    <RegistrationStatsTable
                        :registrations="expandedByStatus.ACCEPTED"
                        :stats="registrationStats"
                        :show-actions="false"
                    />
                  </section>

                  <!-- Denied -->
                  <section v-if="expandedByStatus.DENIED.length > 0" class="space-y-2">
                    <SubHeader>
                      <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
                      {{ t('eventsRegistrations.groupDenied') }} ({{ expandedByStatus.DENIED.length }})
                    </SubHeader>
                    <RegistrationStatsTable
                        :registrations="expandedByStatus.DENIED"
                        :stats="registrationStats"
                        :show-actions="false"
                    />
                  </section>

                  <!-- Declined -->
                  <section v-if="expandedByStatus.DECLINED.length > 0" class="space-y-2">
                    <SubHeader>
                      <font-awesome-icon :icon="['fas', 'user-slash']" class="mr-1"/>
                      {{ t('eventsRegistrations.groupDeclined') }} ({{ expandedByStatus.DECLINED.length }})
                    </SubHeader>
                    <RegistrationStatsTable
                        :registrations="expandedByStatus.DECLINED"
                        :stats="registrationStats"
                        :show-actions="false"
                    />
                  </section>

                  <!-- Withdrawn -->
                  <section v-if="expandedByStatus.WITHDRAWN.length > 0" class="space-y-2">
                    <SubHeader>
                      <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1"/>
                      {{ t('eventsRegistrations.groupWithdrawn') }} ({{ expandedByStatus.WITHDRAWN.length }})
                    </SubHeader>
                    <RegistrationStatsTable
                        :registrations="expandedByStatus.WITHDRAWN"
                        :stats="registrationStats"
                        :show-actions="false"
                    />
                  </section>
                </template>
              </div>
            </template>
          </component>
        </div>
      </template>
    </div>
  </ViewContent>
</template>

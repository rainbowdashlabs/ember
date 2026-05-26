/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {events, stationMembers} from '@/api'
import type {StationEvent, StationMember} from '@/api/types'
import {RegistrationStatus} from '@/api/types'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'

const {t} = useI18n()

const pendingRegistrations = ref<EventRegistrationEntry[]>([])
const allEvents = ref<StationEvent[]>([])
const allMembers = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const expandedEventId = ref<number | null>(null)
const registrationStats = ref<MemberRegistrationStats[]>([])

interface EventGroup {
  event: StationEvent
  pending: EventRegistrationEntry[]
  accepted: number
  deadlineExpired: boolean
}

const eventGroups = computed((): EventGroup[] => {
  const groups = new Map<number, EventRegistrationEntry[]>()
  for (const reg of pendingRegistrations.value) {
    const list = groups.get(reg.eventId) ?? []
    list.push(reg)
    groups.set(reg.eventId, list)
  }

  const result: EventGroup[] = []
  for (const [eventId, regs] of groups) {
    const event = allEvents.value.find(e => e.id === eventId)
    if (!event) continue
    const acceptedCount = pendingRegistrations.value
        .filter(r => r.eventId === eventId && r.status === RegistrationStatus.ACCEPTED).length
    // Actually count all accepted for this event (not just from pending list)
    const deadlineExpired = event.registrationDeadline
        ? new Date(event.registrationDeadline) < new Date()
        : false
    result.push({event, pending: regs, accepted: acceptedCount, deadlineExpired})
  }

  // Sort by deadline ascending (null = end)
  result.sort((a, b) => {
    const da = a.event.registrationDeadline ?? '9999'
    const db = b.event.registrationDeadline ?? '9999'
    return da.localeCompare(db)
  })
  return result
})

function getStats(memberId: number): MemberRegistrationStats | undefined {
  return registrationStats.value.find(s => s.memberId === memberId)
}

function memberName(memberId: number): string {
  const m = allMembers.value.find(m => m.id === memberId)
  if (!m) return `#${memberId}`
  return m.name && m.name.trim() ? m.name : m.email ?? `#${memberId}`
}

function formatDeadline(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'})
}

async function toggleExpand(eventId: number) {
  if (expandedEventId.value === eventId) {
    expandedEventId.value = null
    return
  }
  expandedEventId.value = eventId
  try {
    registrationStats.value = await events.getRegistrationStats(eventId)
  } catch { /* ignore */ }
}

async function accept(regId: number) {
  error.value = ''
  try {
    await events.updateRegistrationStatus(regId, RegistrationStatus.ACCEPTED)
    pendingRegistrations.value = pendingRegistrations.value.filter(r => r.id !== regId)
  } catch { error.value = t('common.error') }
}

async function deny(regId: number) {
  error.value = ''
  try {
    await events.updateRegistrationStatus(regId, RegistrationStatus.DENIED)
    pendingRegistrations.value = pendingRegistrations.value.filter(r => r.id !== regId)
  } catch { error.value = t('common.error') }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [regs, evs, members] = await Promise.all([
      events.listPendingRegistrations(),
      events.listEvents(),
      stationMembers.listMembers(),
    ])
    pendingRegistrations.value = regs
    allEvents.value = evs
    allMembers.value = members
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
              <div class="flex items-center gap-2">
                <InfoBadge>{{ group.pending.length }} {{ t('eventsRegistrations.pending') }}</InfoBadge>
                <SuccessBadge v-if="group.accepted > 0">{{ group.accepted }} {{ t('eventsRegistrations.accepted') }}</SuccessBadge>
                <ErrorBadge v-if="group.deadlineExpired">{{ t('eventsRegistrations.expired') }}</ErrorBadge>
              </div>
            </div>

            <!-- Expanded: fair acceptance table -->
            <template v-if="expandedEventId === group.event.id">
              <div class="border-t border-(--border) pt-3" @click.stop>
                <table class="w-full text-sm">
                  <thead>
                    <tr class="text-xs text-(--text-muted) border-b border-(--border)">
                      <th class="p-2 text-left">{{ t('eventsRegistrations.member') }}</th>
                      <th class="p-2 text-center">{{ t('eventsRegistrations.score') }}</th>
                      <th class="p-2 text-center">{{ t('eventsRegistrations.acceptedCol') }}</th>
                      <th class="p-2 text-center">{{ t('eventsRegistrations.deniedCol') }}</th>
                      <th class="p-2 text-center">{{ t('eventsRegistrations.date') }}</th>
                      <th class="p-2"></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="reg in group.pending" :key="reg.id" class="border-b border-(--border) last:border-0">
                      <td class="p-2">
                        <MemberName :name="memberName(reg.memberId)" :member-id="reg.memberId"/>
                      </td>
                      <td class="p-2 text-center font-bold" :class="getStats(reg.memberId)?.priority === 'HIGH' ? 'text-error' : getStats(reg.memberId)?.priority === 'MEDIUM' ? 'text-info' : ''">
                        {{ getStats(reg.memberId)?.fairnessScore ?? '-' }}
                      </td>
                      <td class="p-2 text-center">
                        <SuccessBadge v-if="getStats(reg.memberId)">{{ getStats(reg.memberId)!.accepted }}</SuccessBadge>
                      </td>
                      <td class="p-2 text-center">
                        <ErrorBadge v-if="getStats(reg.memberId)?.denied">{{ getStats(reg.memberId)!.denied }}</ErrorBadge>
                        <span v-else>0</span>
                      </td>
                      <td class="p-2 text-center text-xs text-(--text-muted)">{{ reg.eventDate }}</td>
                      <td class="p-2">
                        <div class="flex items-center gap-1 justify-end">
                          <PrimaryButton compact :icon="['fas', 'check']" @click="accept(reg.id)">
                            {{ t('eventsRegistrations.accept') }}
                          </PrimaryButton>
                          <ErrorButton compact :icon="['fas', 'xmark']" @click="deny(reg.id)">
                            {{ t('eventsRegistrations.deny') }}
                          </ErrorButton>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </component>
        </div>
      </template>
    </div>
  </ViewContent>
</template>

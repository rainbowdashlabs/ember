/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {StationEvent} from '@/api/events'
import {RegistrationStatus} from '@/api/events'
import {StationPermission} from '@/api/types'
import type {EventRegistrationEntry, MemberRegistrationStats, FederatedEventRegistration} from '@/api/events'
import {events, stationMembers as stationMembersApi} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncAction} from '@/composables/useAsyncAction'
import RegistrationsPanel from './RegistrationsPanel.vue'
import FederatedRegistrationsPanel from './FederatedRegistrationsPanel.vue'

const props = defineProps<{
  event: StationEvent
  eventId: number
  currentMemberId: number
  registrableMembers: { id: number; name: string }[]
  hasManagedMembers: boolean
  nextOccurrenceDate: string | null
}>()

const {t} = useI18n()
const {canManageEvents, hasPermission} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const registrations = ref<EventRegistrationEntry[]>([])
const registrationStats = ref<MemberRegistrationStats[]>([])
const federatedRegs = ref<FederatedEventRegistration[]>([])
interface MemberOption {
  id: number
  name: string
}

const allMembers = ref<MemberOption[]>([])
const error = ref('')
const manualRegisterMemberId = ref('')

interface StatusGroup { status: string; entries: EventRegistrationEntry[] }

const pendingRegistrations = computed(() => {
  const pending = registrations.value.filter(r => r.status === RegistrationStatus.PENDING)
  return [...pending].sort((a, b) => {
    const sa = registrationStats.value.find(s => s.memberId === a.memberId)
    const sb = registrationStats.value.find(s => s.memberId === b.memberId)
    return (sb?.fairnessScore ?? 0) - (sa?.fairnessScore ?? 0)
  })
})

const nonPendingRegistrations = computed<StatusGroup[]>(() => {
  const byStatus = new Map<string, EventRegistrationEntry[]>()
  for (const reg of registrations.value) {
    if (reg.status === RegistrationStatus.PENDING) continue
    const list = byStatus.get(reg.status) ?? []
    list.push(reg)
    byStatus.set(reg.status, list)
  }
  for (const list of byStatus.values()) {
    list.sort((a, b) => a.memberName.localeCompare(b.memberName, 'de'))
  }
  return [RegistrationStatus.ACCEPTED, RegistrationStatus.DECLINED, RegistrationStatus.DENIED]
      .filter(s => byStatus.has(s))
      .map(s => ({status: s, entries: byStatus.get(s)!}))
})

const unregisteredMembers = computed(() => {
  const regIds = new Set(registrations.value.map(r => r.memberId))
  return allMembers.value.filter(m => !regIds.has(m.id)).sort((a, b) => a.name.localeCompare(b.name))
})

function getRegistrationForMember(memberId: number): EventRegistrationEntry | undefined {
  return registrations.value.find(r => r.memberId === memberId)
}

function statusLabel(status: string): string {
  if (status === RegistrationStatus.ACCEPTED) return t('eventsUpcoming.statusAccepted')
  if (status === RegistrationStatus.PENDING) return t('eventsUpcoming.statusPending')
  if (status === RegistrationStatus.DENIED) return t('eventsUpcoming.statusDenied')
  if (status === RegistrationStatus.DECLINED) return t('eventsUpcoming.statusDeclined')
  return status
}

async function loadRegistrations() {
  try {
    registrations.value = await events.listEventRegistrations(props.eventId)
    if (hasPermission(StationPermission.EVENT_REGISTRATION) && props.event.requiresRegistration) {
      registrationStats.value = await events.getRegistrationStats(
          props.eventId, props.event.categoryId ?? undefined)
    }
    if (hasPermission(StationPermission.EVENT_REGISTRATION)) {
      federatedRegs.value = await events.listFederationRegistrations(props.eventId).catch(() => [])
      const members = await stationMembersApi.listMembers().catch(() => [])
      allMembers.value = members.map(m => ({id: m.id, name: m.name ?? m.email ?? `#${m.id}`}))
    }
  } catch {
    error.value = t('common.error')
  }
}

async function reloadAndRefresh() {
  await loadRegistrations()
  refreshSidebarCounts()
}

async function acceptRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.ACCEPTED)
    await reloadAndRefresh()
  } catch { error.value = t('common.error') }
}

async function denyRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.DENIED)
    await reloadAndRefresh()
  } catch { error.value = t('common.error') }
}

const {running: registering, error: registrationError, run: runRegistration} = useAsyncAction(
    async (kind: 'register' | 'decline', memberId: number) => {
      const request = {
        eventDate: props.nextOccurrenceDate ?? undefined,
        memberId: memberId !== props.currentMemberId ? memberId : undefined,
      }
      if (kind === 'register') {
        await events.registerForEvent(props.eventId, request)
      } else {
        await events.declineEvent(props.eventId, request)
      }
      await reloadAndRefresh()
    },
    {formatError: () => t('common.error')},
)

function registerMember(memberId: number) {
  return runRegistration('register', memberId)
}

function declineMember(memberId: number) {
  return runRegistration('decline', memberId)
}

async function acceptFederatedReg(regId: number) {
  await events.updateFederationRegistrationStatus(regId, 'ACCEPTED')
  await loadRegistrations()
}

async function denyFederatedReg(regId: number) {
  await events.updateFederationRegistrationStatus(regId, 'DENIED')
  await loadRegistrations()
}

async function manualRegister() {
  if (!manualRegisterMemberId.value) return
  try {
    await events.registerForEvent(props.eventId, {
      eventDate: props.nextOccurrenceDate ?? undefined,
      memberId: Number(manualRegisterMemberId.value),
    })
    manualRegisterMemberId.value = ''
    await reloadAndRefresh()
  } catch { error.value = t('common.error') }
}

defineExpose({loadRegistrations})
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="error || registrationError" variant="error">{{ error || registrationError }}</Alert>

    <NeutralContainer v-if="event.requiresRegistration && !canManageEvents()" class="space-y-3">
      <SubHeader>{{ t('eventDetail.myRegistration') }}</SubHeader>
      <div v-for="member in registrableMembers" :key="member.id" class="flex items-center gap-3 flex-wrap">
        <span v-if="hasManagedMembers" class="text-sm font-medium min-w-24">{{ member.name }}</span>
        <template v-if="getRegistrationForMember(member.id)">
          <component :is="getRegistrationForMember(member.id)!.status === RegistrationStatus.ACCEPTED ? SuccessBadge : getRegistrationForMember(member.id)!.status === RegistrationStatus.PENDING ? InfoBadge : ErrorBadge">
            {{ statusLabel(getRegistrationForMember(member.id)!.status) }}
          </component>
          <ErrorButton v-if="getRegistrationForMember(member.id)!.status !== RegistrationStatus.DECLINED" :disabled="registering" @click="declineMember(member.id)">
            <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>{{ t('eventsUpcoming.decline') }}
          </ErrorButton>
          <PrimaryButton v-if="getRegistrationForMember(member.id)!.status === RegistrationStatus.DECLINED" :disabled="registering" @click="registerMember(member.id)">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ t('eventsUpcoming.register') }}
          </PrimaryButton>
        </template>
        <template v-else>
          <PrimaryButton :disabled="registering" @click="registerMember(member.id)">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ t('eventsUpcoming.register') }}
          </PrimaryButton>
          <ErrorButton :disabled="registering" @click="declineMember(member.id)">
            <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>{{ t('eventsUpcoming.decline') }}
          </ErrorButton>
        </template>
      </div>
    </NeutralContainer>

    <RegistrationsPanel
        :event="event"
        :registrations="registrations"
        :pending-registrations="pendingRegistrations"
        :non-pending-registrations="nonPendingRegistrations"
        :registration-stats="registrationStats"
        :unregistered-members="unregisteredMembers"
        v-model:manual-register-member-id="manualRegisterMemberId"
        @accept="acceptRegistration"
        @deny="denyRegistration"
        @manual-register="manualRegister"
    />

    <FederatedRegistrationsPanel
        v-if="canManageEvents()"
        :registrations="federatedRegs"
        @accept="acceptFederatedReg"
        @deny="denyFederatedReg"
    />
  </div>
</template>

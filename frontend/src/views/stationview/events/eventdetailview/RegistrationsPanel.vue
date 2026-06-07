/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {RegistrationStatus, StationPermission} from '@/api/types'
import type {StationEvent} from '@/api/types'
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'
import {useSession} from '@/composables/useSession'

interface StatusGroup { status: string; entries: EventRegistrationEntry[] }

const props = defineProps<{
  event: StationEvent
  registrations: EventRegistrationEntry[]
  pendingRegistrations: EventRegistrationEntry[]
  nonPendingRegistrations: StatusGroup[]
  registrationStats: MemberRegistrationStats[]
  unregisteredMembers: { id: number; name: string }[]
}>()

const manualRegisterMemberId = defineModel<string>('manualRegisterMemberId', {default: ''})

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
  manualRegister: []
}>()

const {t} = useI18n()
const {hasPermission} = useSession()

const registrationSummary = computed(() => {
  let accepted = 0, pending = 0, denied = 0, declined = 0
  for (const r of props.registrations) {
    if (r.status === RegistrationStatus.ACCEPTED) accepted++
    else if (r.status === RegistrationStatus.PENDING) pending++
    else if (r.status === RegistrationStatus.DENIED) denied++
    else if (r.status === RegistrationStatus.DECLINED) declined++
  }
  return {accepted, pending, denied, declined}
})

function statusLabel(status: string): string {
  if (status === RegistrationStatus.ACCEPTED) return t('eventsUpcoming.statusAccepted')
  if (status === RegistrationStatus.PENDING) return t('eventsUpcoming.statusPending')
  if (status === RegistrationStatus.DENIED) return t('eventsUpcoming.statusDenied')
  if (status === RegistrationStatus.DECLINED) return t('eventsUpcoming.statusDeclined')
  return status
}

function getStatsForMember(memberId: number): MemberRegistrationStats | undefined {
  return props.registrationStats.find(s => s.memberId === memberId)
}

function formatDate(iso: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}
</script>

<template>
  <NeutralContainer v-if="registrations.length > 0" class="space-y-4">
    <SubHeader>{{ t('eventDetail.registrations') }}</SubHeader>

    <div v-if="registrations.length > 0" class="flex flex-wrap gap-2">
      <SuccessBadge v-if="registrationSummary.accepted > 0">
        {{ registrationSummary.accepted }} {{ t('eventsUpcoming.accepted') }}
      </SuccessBadge>
      <InfoBadge v-if="registrationSummary.pending > 0">
        {{ registrationSummary.pending }} {{ t('eventsUpcoming.pendingCount') }}
      </InfoBadge>
      <ErrorBadge v-if="registrationSummary.denied > 0">
        {{ registrationSummary.denied }} {{ t('eventsRegistrations.deny') }}
      </ErrorBadge>
      <PrimaryBadge v-if="registrationSummary.declined > 0">
        {{ registrationSummary.declined }} {{ t('eventsUpcoming.declinedCount') }}
      </PrimaryBadge>
    </div>

    <div v-if="pendingRegistrations.length > 0" class="space-y-2">
      <SubHeader>{{ statusLabel('PENDING') }}</SubHeader>
      <div class="overflow-x-auto">
        <table class="w-full text-sm border-collapse">
          <thead v-if="hasPermission(StationPermission.EVENT_REGISTRATION) && registrationStats.length > 0">
          <tr class="border-b border-(--border) text-left text-xs text-(--text-muted) uppercase">
            <th class="p-2">{{ t('registrationStats.member') }}</th>
            <th class="p-2 text-center">{{ t('registrationStats.score') }}</th>
            <th class="p-2 text-center">{{ t('registrationStats.accepted') }}</th>
            <th class="p-2 text-center">{{ t('registrationStats.denied') }}</th>
            <th class="p-2 text-center">{{ t('registrationStats.rate') }}</th>
            <th class="p-2"></th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="reg in pendingRegistrations" :key="reg.id" class="border-b border-(--border)">
            <td class="p-2"><MemberName :identity="reg.memberIdentity ?? null"/></td>
            <template v-if="hasPermission(StationPermission.EVENT_REGISTRATION) && getStatsForMember(reg.memberId)">
              <td class="p-2 text-center font-bold" :class="getStatsForMember(reg.memberId)!.priority === 'HIGH' ? 'text-[var(--error)]' : getStatsForMember(reg.memberId)!.priority === 'MEDIUM' ? 'text-[var(--info)]' : ''">
                {{ getStatsForMember(reg.memberId)!.fairnessScore }}
              </td>
              <td class="p-2 text-center"><SuccessBadge>{{ getStatsForMember(reg.memberId)!.accepted }}</SuccessBadge></td>
              <td class="p-2 text-center">
                <ErrorBadge v-if="getStatsForMember(reg.memberId)!.denied > 0">{{ getStatsForMember(reg.memberId)!.denied }}</ErrorBadge>
                <span v-else>0</span>
              </td>
              <td class="p-2 text-center">{{ Math.round(getStatsForMember(reg.memberId)!.acceptRate * 100) }}%</td>
            </template>
            <template v-else>
              <td class="p-2 text-center text-(--text-muted)" colspan="4">–</td>
            </template>
            <td class="p-2">
              <div v-if="hasPermission(StationPermission.EVENT_REGISTRATION) && event.requiresConfirmation" class="flex items-center gap-2 justify-end">
                <PrimaryButton @click="emit('accept', reg.id)">
                  <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                  {{ t('eventsRegistrations.accept') }}
                </PrimaryButton>
                <ErrorButton @click="emit('deny', reg.id)">
                  <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
                  {{ t('eventsRegistrations.deny') }}
                </ErrorButton>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-for="group in nonPendingRegistrations" :key="group.status" class="space-y-2">
      <SubHeader>{{ statusLabel(group.status) }}</SubHeader>
      <NeutralContainer v-for="reg in group.entries" :key="reg.id">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div class="flex items-center gap-2">
            <MemberName :identity="reg.memberIdentity ?? null"/>
            <span v-if="reg.eventDate" class="text-xs text-(--text-muted)">{{ formatDate(reg.eventDate) }}</span>
          </div>
          <div v-if="hasPermission(StationPermission.EVENT_REGISTRATION)" class="flex items-center gap-2">
            <PrimaryButton v-if="reg.status !== RegistrationStatus.ACCEPTED" @click="emit('accept', reg.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('eventsRegistrations.accept') }}
            </PrimaryButton>
            <ErrorButton v-if="reg.status !== RegistrationStatus.DENIED" @click="emit('deny', reg.id)">
              <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
              {{ t('eventsRegistrations.deny') }}
            </ErrorButton>
          </div>
        </div>
      </NeutralContainer>
    </div>

    <div v-if="hasPermission(StationPermission.EVENT_REGISTRATION) && unregisteredMembers.length > 0" class="pt-2">
      <SubHeader>{{ t('eventDetail.manualRegister') }}</SubHeader>
      <div class="flex items-center gap-2">
        <SelectInput v-model="manualRegisterMemberId" class="flex-1">
          <option value="">{{ t('eventDetail.selectMember') }}</option>
          <option v-for="m in unregisteredMembers" :key="m.id" :value="String(m.id)">{{ m.name }}</option>
        </SelectInput>
        <PrimaryButton :disabled="!manualRegisterMemberId" @click="emit('manualRegister')">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
          {{ t('eventsUpcoming.register') }}
        </PrimaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>

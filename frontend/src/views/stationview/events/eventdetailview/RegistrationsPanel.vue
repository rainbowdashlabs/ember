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
import RegistrationStatsTable from './RegistrationStatsTable.vue'
import RegistrationFieldAnswers from './RegistrationFieldAnswers.vue'
import {EventFieldTypes, RegistrationStatus, type EventRegistrationEntry, type EventRegistrationField, type MemberRegistrationStats, type StationEvent} from '@/api/events'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {formatDate} from '@/util/format'

interface StatusGroup { status: string; entries: EventRegistrationEntry[] }

const props = defineProps<{
  event: StationEvent
  registrations: EventRegistrationEntry[]
  pendingRegistrations: EventRegistrationEntry[]
  nonPendingRegistrations: StatusGroup[]
  registrationStats: MemberRegistrationStats[]
  unregisteredMembers: { id: number; name: string }[]
  registrationFields?: EventRegistrationField[]
}>()

const {t} = useI18n()
const {hasPermission} = useSession()

const fields = computed(() => props.registrationFields ?? [])

/** Whoever runs the event sees every answer and the totals; everyone else sees the list columns. */
const runsEvent = computed(() => hasPermission(StationPermission.EVENT_EDIT))

function answersOf(fieldId: number): string[] {
  return props.registrations
      .map(registration => registration.fields?.find(value => value.fieldId === fieldId)?.value)
      .filter((value): value is string => value != null && value !== '')
}

/**
 * Totals for the questions that have a countable answer, which is what a station plans from: a
 * number question is summed, a choice question is counted per option. Free text has no total worth
 * showing, so it gets none.
 */
const summaries = computed(() => {
  if (!runsEvent.value) return []
  const result: { label: string; text: string }[] = []
  for (const field of fields.value) {
    if (field.fieldType === EventFieldTypes.NUMBER) {
      const total = answersOf(field.id)
          .map(Number)
          .filter(value => !Number.isNaN(value))
          .reduce((sum, value) => sum + value, 0)
      result.push({label: field.name, text: String(total)})
      continue
    }
    if (field.fieldType === EventFieldTypes.ENUM) {
      const answers = answersOf(field.id)
      const counts = (field.config?.options ?? [])
          .map(option => ({option, count: answers.filter(answer => answer === option).length}))
          .filter(entry => entry.count > 0)
      if (counts.length > 0) {
        result.push({label: field.name, text: counts.map(e => `${e.option} ${e.count}`).join(', ')})
      }
    }
  }
  return result
})

const manualRegisterMemberId = defineModel<string>('manualRegisterMemberId', {default: ''})

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
  manualRegister: []
}>()

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

</script>

<template>
  <NeutralContainer v-if="registrations.length > 0" class="space-y-4">
    <SubHeader>{{ t('eventDetail.registrations') }}</SubHeader>

    <div v-if="summaries.length > 0" class="flex flex-wrap gap-x-4 gap-y-1 text-sm">
      <span v-for="summary in summaries" :key="summary.label" class="text-(--text-muted)">
        {{ summary.label }}: <span class="text-(--text) font-medium">{{ summary.text }}</span>
      </span>
    </div>

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
      <!-- Manager view: table with stats and action buttons -->
      <RegistrationStatsTable
          v-if="hasPermission(StationPermission.EVENT_REGISTRATION)"
          :fields="runsEvent ? fields : []"
          :registrations="pendingRegistrations"
          :stats="registrationStats"
          :show-actions="event.requiresConfirmation"
          @accept="emit('accept', $event)"
          @deny="emit('deny', $event)"
      />
      <!-- Non-manager view: card display matching confirmed registrations -->
      <template v-else>
        <NeutralContainer v-for="reg in pendingRegistrations" :key="reg.id">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <div class="flex items-center gap-2">
              <MemberName :identity="reg.memberIdentity ?? null"/>
              <span v-if="reg.eventDate" class="text-xs text-(--text-muted)">{{ formatDate(reg.eventDate) }}</span>
            </div>
            <InfoBadge>{{ statusLabel('PENDING') }}</InfoBadge>
          </div>
          <RegistrationFieldAnswers :fields="fields" :values="reg.fields" :overview-only="!runsEvent" class="mt-1"/>
        </NeutralContainer>
      </template>
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
        <RegistrationFieldAnswers :fields="fields" :values="reg.fields" :overview-only="!runsEvent" class="mt-1"/>
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

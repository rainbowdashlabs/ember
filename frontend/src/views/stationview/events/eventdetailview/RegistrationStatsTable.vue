/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {EventRegistrationEntry, EventRegistrationField, MemberRegistrationStats} from '@/api/events'
import {emptyTableState, useDataTable} from '@/composables/useDataTable'
import RegistrationStatsMember from './registrationstatstable/RegistrationStatsMember.vue'
import {registrationStatsColumns, SCORE_KEY, type RankedRegistration} from './registrationstatstable/registrationStatsColumns'

/**
 * The sign-ups ranked by how strong a claim each member has to a place: the highest score first,
 * which a reader may resort or filter by any column.
 */
const props = defineProps<{
  fields?: EventRegistrationField[]
  registrations: EventRegistrationEntry[]
  stats: MemberRegistrationStats[]
  showActions?: boolean
  /** Whether the reader may put an answer right, which is whoever runs the appointment. */
  canEditAnswers?: boolean
}>()

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
  editAnswers: [registrationId: number]
}>()

const {t} = useI18n()

const statsByMember = computed(() => new Map(props.stats.map(stats => [stats.memberId, stats])))

const rows = computed<RankedRegistration[]>(() => props.registrations.map(registration => ({
  registration,
  stats: statsByMember.value.get(registration.memberId) ?? null,
})))

const table = useDataTable<RankedRegistration>({
  id: 'registration-stats',
  rows,
  columns: computed(() => registrationStatsColumns(t)),
  rowKey: row => row.registration.id,
  state: ref(emptyTableState(SCORE_KEY, 'desc')),
})

/** The colour the ranking is read by: the ones with the strongest claim to a place stand out. */
function scoreClass(row: RankedRegistration): string {
  if (row.stats?.priority === 'HIGH') return 'font-bold text-success'
  if (row.stats?.priority === 'MEDIUM') return 'font-bold text-info'
  return 'font-bold'
}
</script>

<template>
  <div class="space-y-2">
    <div class="flex justify-end">
      <TableColumnPicker :table="table"/>
    </div>
    <RecordTable :table="table" plain test-id="registration-stats-table">
      <template #cell-member="{row}">
        <RegistrationStatsMember
            :can-edit-answers="canEditAnswers ?? false"
            :fields="fields ?? []"
            :registration="row.registration"
            @edit-answers="emit('editAnswers', $event)"
        />
      </template>
      <template #cell-score="{row, text}">
        <span :class="scoreClass(row)">{{ text }}</span>
      </template>
      <template #cell-accepted="{text}">
        <SuccessBadge v-if="text">{{ text }}</SuccessBadge>
      </template>
      <template #cell-denied="{row, text}">
        <ErrorBadge v-if="(row.stats?.denied ?? 0) > 0">{{ text }}</ErrorBadge>
        <template v-else>{{ text }}</template>
      </template>
      <template v-if="showActions" #actions="{row}">
        <ButtonRow pair align="end">
          <PrimaryButton :icon="['fas', 'check']" @click="emit('accept', row.registration.id)">
            {{ t('eventsRegistrations.accept') }}
          </PrimaryButton>
          <ErrorButton :icon="['fas', 'xmark']" @click="emit('deny', row.registration.id)">
            {{ t('eventsRegistrations.deny') }}
          </ErrorButton>
        </ButtonRow>
      </template>
    </RecordTable>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import {useDataTable} from '@/composables/useDataTable'
import {attendanceColumns} from '@/views/stationview/events/eventgroupdetailview/eventGroupTables'
import type {EventGroupAttendance} from '@/api/generated/schema'
import {exampleAttendance, exampleSets} from './exampleSignUps'

/** The attendance overview of the event group page as the help shows it: a column per set. */
const {t} = useI18n()

const table = useDataTable<EventGroupAttendance>({
  id: 'help-event-group-attendance',
  rows: computed(() => exampleAttendance(t)),
  columns: computed(() => attendanceColumns(exampleSets(t), t)),
  rowKey: row => row.memberId,
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ t('eventGroups.attendance') }}</SubHeader>
      <ButtonRow align="end">
        <TableColumnPicker :table="table"/>
        <SecondaryButton :icon="['fas', 'file-export']">{{ t('eventGroups.attendanceExport') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <RecordTable :table="table" test-id="help-event-group-attendance" plain>
      <template #cell-complete="{row}">
        <SuccessBadge v-if="row.complete">{{ t('eventGroups.attendanceComplete') }}</SuccessBadge>
        <ErrorBadge v-else>{{ t('eventGroups.attendanceOpen') }}</ErrorBadge>
      </template>
    </RecordTable>
  </NeutralContainer>
</template>

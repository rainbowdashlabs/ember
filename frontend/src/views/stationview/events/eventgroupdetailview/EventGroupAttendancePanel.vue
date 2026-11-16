/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ExportFormatModal from '@/components/documents/ExportFormatModal.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import {attendanceColumns} from './eventGroupTables'
import {useDataTable} from '@/composables/useDataTable'
import {byValue} from '@/composables/useSortable'
import type {EventGroupAttendance, EventGroupDetail} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {presentFile} from '@/util/documentFile'
import type {ExportSeparator} from '@/util/exportFormat'

/**
 * How the accepted participants attended the parts, read from each part's attendance sheet: per set
 * the parts attended against its minimum, and whether everything is met. Exported as a spreadsheet
 * or as a sheet to print.
 *
 * <p>A table of records with a column per set of the event group, so it sorts by any set, filters
 * down to the participants who have not met everything yet, and shows a card per participant on a
 * phone.
 */
const props = defineProps<{
  detail: EventGroupDetail
}>()

const {t} = useI18n()

const rows = ref<EventGroupAttendance[]>([])
const exportOpen = ref(false)

const {loading, failure, reload} = useAsyncLoader(async () => {
  rows.value = await eventGroups.listAttendance(props.detail.eventGroup.id)
})

watch(() => props.detail.eventGroup.id, () => reload())

const {running: exporting, failure: exportFailure, run: exportFile} = useAsyncAction(
    async (format: 'csv' | 'pdf', separator: ExportSeparator) => {
      await presentFile(await eventGroups.exportAttendance(props.detail.eventGroup.id, format, separator))
      exportOpen.value = false
    })

const columns = computed(() => attendanceColumns(props.detail.sets, t))

const table = useDataTable<EventGroupAttendance>({
  id: 'event-group-attendance',
  rows,
  columns,
  rowKey: row => row.memberId,
  fallbackSort: byValue(row => row.member.name ?? ''),
})

defineExpose({reload})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-group-attendance">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SectionHeader>{{ t('eventGroups.attendance') }}</SectionHeader>
      <ButtonRow align="end">
        <TableColumnPicker :table="table"/>
        <SecondaryButton :icon="['fas', 'file-export']" :disabled="exporting || rows.length === 0"
                         data-testid="event-group-attendance-export" @click="exportOpen = true">
          {{ t('eventGroups.attendanceExport') }}
        </SecondaryButton>
      </ButtonRow>
    </div>
    <MutedText tag="p" size="sm">{{ t('eventGroups.attendanceHint') }}</MutedText>
    <FailureAlert v-if="exportFailure" :failure="exportFailure"/>
    <AsyncSection :loading="loading" :failure="failure" :empty="rows.length === 0"
                  :empty-message="t('eventGroups.attendanceEmpty')" empty-compact>
      <RecordTable :table="table" test-id="event-group-attendance-table" row-test-id="event-group-attendance-row" plain>
        <template #cell-member="{row}">
          <MemberName :identity="row.member"/>
        </template>
        <template #cell-complete="{row}">
          <SuccessBadge v-if="row.complete">{{ t('eventGroups.attendanceComplete') }}</SuccessBadge>
          <ErrorBadge v-else>{{ t('eventGroups.attendanceOpen') }}</ErrorBadge>
        </template>
      </RecordTable>
    </AsyncSection>
    <ExportFormatModal v-model="exportOpen" :formats="['csv', 'pdf']" :exporting="exporting" @export="exportFile"/>
  </NeutralContainer>
</template>

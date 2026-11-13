/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
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
import type {EventGroupAttendance, EventGroupDetail} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {presentFile} from '@/util/documentFile'
import {describeFailure, type Failure} from '@/util/failure'
import type {ExportSeparator} from '@/util/exportFormat'

/**
 * How the accepted participants attended the parts, read from each part's attendance sheet: per set
 * the parts attended against its minimum, and whether everything is met. Exported as a spreadsheet.
 */
const props = defineProps<{
  detail: EventGroupDetail
}>()

const {t} = useI18n()

const rows = ref<EventGroupAttendance[]>([])
const exporting = ref(false)
const exportOpen = ref(false)
const exportFailure = ref<Failure | null>(null)

const {loading, failure, reload} = useAsyncLoader(async () => {
  rows.value = await eventGroups.listAttendance(props.detail.eventGroup.id)
})

watch(() => props.detail.eventGroup.id, () => reload())

async function exportCsv(_format: 'csv' | 'pdf', separator: ExportSeparator) {
  exporting.value = true
  exportFailure.value = null
  try {
    await presentFile(await eventGroups.exportAttendance(props.detail.eventGroup.id, separator))
    exportOpen.value = false
  } catch (e) {
    exportFailure.value = describeFailure(e, t)
  } finally {
    exporting.value = false
  }
}

defineExpose({reload})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-group-attendance">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SectionHeader>{{ t('eventGroups.attendance') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'file-csv']" :disabled="exporting || rows.length === 0"
                       data-testid="event-group-attendance-export" @click="exportOpen = true">
        {{ t('eventGroups.attendanceExport') }}
      </SecondaryButton>
    </div>
    <MutedText tag="p" size="sm">{{ t('eventGroups.attendanceHint') }}</MutedText>
    <FailureAlert v-if="exportFailure" :failure="exportFailure"/>
    <AsyncSection :loading="loading" :failure="failure" :empty="rows.length === 0"
                  :empty-message="t('eventGroups.attendanceEmpty')" empty-compact>
      <ul class="divide-y divide-(--border)">
        <li v-for="row in rows" :key="row.memberId" class="flex flex-wrap items-center gap-2 py-2"
            data-testid="event-group-attendance-row">
          <MemberName :identity="row.member"/>
          <MutedText v-for="set in row.sets" :key="set.setId" tag="span" size="sm">
            {{ t('eventGroups.attendanceSet', {name: set.name, attended: set.chosen, required: set.required}) }}
          </MutedText>
          <SuccessBadge v-if="row.complete">{{ t('eventGroups.attendanceComplete') }}</SuccessBadge>
          <ErrorBadge v-else>{{ t('eventGroups.attendanceOpen') }}</ErrorBadge>
        </li>
      </ul>
    </AsyncSection>
    <ExportFormatModal v-model="exportOpen" :formats="['csv']" :exporting="exporting" @export="exportCsv"/>
  </NeutralContainer>
</template>

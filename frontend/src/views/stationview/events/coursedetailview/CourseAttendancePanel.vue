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
import type {CourseAttendance, CourseDetail} from '@/api/generated/schema'
import {eventCourses} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {presentFile} from '@/util/documentFile'
import {describeFailure, type Failure} from '@/util/failure'
import type {ExportSeparator} from '@/util/exportFormat'

/**
 * How the accepted participants attended the parts, read from each part's attendance sheet: per set
 * the parts attended against its minimum, and whether everything is met. Exported as a spreadsheet.
 */
const props = defineProps<{
  detail: CourseDetail
}>()

const {t} = useI18n()

const rows = ref<CourseAttendance[]>([])
const exporting = ref(false)
const exportOpen = ref(false)
const exportFailure = ref<Failure | null>(null)

const {loading, failure, reload} = useAsyncLoader(async () => {
  rows.value = await eventCourses.listAttendance(props.detail.course.id)
})

watch(() => props.detail.course.id, () => reload())

async function exportCsv(_format: 'csv' | 'pdf', separator: ExportSeparator) {
  exporting.value = true
  exportFailure.value = null
  try {
    await presentFile(await eventCourses.exportAttendance(props.detail.course.id, separator))
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
  <NeutralContainer class="space-y-3" data-testid="course-attendance">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SectionHeader>{{ t('courses.attendance') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'file-csv']" :disabled="exporting || rows.length === 0"
                       data-testid="course-attendance-export" @click="exportOpen = true">
        {{ t('courses.attendanceExport') }}
      </SecondaryButton>
    </div>
    <MutedText tag="p" size="sm">{{ t('courses.attendanceHint') }}</MutedText>
    <FailureAlert v-if="exportFailure" :failure="exportFailure"/>
    <AsyncSection :loading="loading" :failure="failure" :empty="rows.length === 0"
                  :empty-message="t('courses.attendanceEmpty')" empty-compact>
      <ul class="divide-y divide-(--border)">
        <li v-for="row in rows" :key="row.memberId" class="flex flex-wrap items-center gap-2 py-2"
            data-testid="course-attendance-row">
          <MemberName :identity="row.member"/>
          <MutedText v-for="set in row.sets" :key="set.setId" tag="span" size="sm">
            {{ t('courses.attendanceSet', {name: set.name, attended: set.chosen, required: set.required}) }}
          </MutedText>
          <SuccessBadge v-if="row.complete">{{ t('courses.attendanceComplete') }}</SuccessBadge>
          <ErrorBadge v-else>{{ t('courses.attendanceOpen') }}</ErrorBadge>
        </li>
      </ul>
    </AsyncSection>
    <ExportFormatModal v-model="exportOpen" :formats="['csv']" :exporting="exporting" @export="exportCsv"/>
  </NeutralContainer>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ReportSummaryTable from './ReportSummaryTable.vue'
import ReportMonthBlock from './ReportMonthBlock.vue'
import ReportSessionCard from './ReportSessionCard.vue'
import type {ReportData} from '@/api/attendance'

const {t} = useI18n()

defineProps<{
  report: ReportData
  exporting: boolean
}>()

const emit = defineEmits<{
  export: []
}>()
</script>

<template>
  <div class="flex justify-end">
    <PrimaryButton :icon="['fas', 'download']" :disabled="exporting" @click="emit('export')">
      {{ exporting ? t('common.loading') : t('attendanceReport.exportPdf') }}
    </PrimaryButton>
  </div>
  <ReportSummaryTable
      :title="`${t('attendanceReport.summary')} – ${report.filterLabel}`"
      :members="report.members"
  />
  <template v-if="report.monthlySummaries.length > 1">
    <ReportMonthBlock v-for="ms in report.monthlySummaries" :key="ms.month" :month-summary="ms"/>
  </template>
  <template v-else>
    <ReportSessionCard v-for="session in report.sessions" :key="session.sessionId" :session="session"/>
  </template>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ReportMonthlySummaryTable from './ReportMonthlySummaryTable.vue'
import ReportSessionCard from './ReportSessionCard.vue'
import type {MonthSummary} from '@/api/attendance'

const {t} = useI18n()

defineProps<{
  monthSummary: MonthSummary
}>()
</script>

<template>
  <SectionHeader>{{ t('attendanceReport.monthReport', {month: monthSummary.month}) }}</SectionHeader>
  <ReportMonthlySummaryTable :members="monthSummary.members"/>
  <ReportSessionCard v-for="session in monthSummary.sessions" :key="session.sessionId" :session="session"/>
</template>

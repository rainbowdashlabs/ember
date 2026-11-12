/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AttendanceMonth} from '@/api/generated/schema'
import {ATTENDANCE_COLORS, CATEGORY_COLORS, seriesChart, useChartPaint} from './instanceCharts'

/**
 * Attendance across the instance per month of the window: the entries stacked by outcome, and the
 * attendance sheets held. A month counts whole once the window reaches into it.
 */
const props = defineProps<{
  months: AttendanceMonth[]
}>()

const {t} = useI18n()
const paint = useChartPaint()

const OUTCOMES = ['present', 'absent', 'declined'] as const

const entriesOption = computed(() => seriesChart(
    'bar',
    t('adminStats.attendancePerMonth'),
    props.months.map(m => m.month),
    OUTCOMES.map(outcome => ({
      name: t(`adminStats.attendanceLabels.${outcome}`),
      data: props.months.map(m => m[outcome]),
      color: ATTENDANCE_COLORS[outcome],
    })),
    paint.value,
    true,
))

const sessionsOption = computed(() => seriesChart(
    'bar',
    t('adminStats.attendanceSessionsPerMonth'),
    props.months.map(m => m.month),
    [{name: t('adminStats.attendanceSessionsPerMonth'), data: props.months.map(m => m.sessions), color: CATEGORY_COLORS[0]}],
    paint.value,
))
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.attendanceSection') }}</SubHeader>
    <div v-if="months.length > 0" class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <NeutralContainer>
        <VChart :option="entriesOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer>
        <VChart :option="sessionsOption" autoresize style="height: 260px"/>
      </NeutralContainer>
    </div>
    <MutedText v-else size="sm" tag="p">{{ t('adminStats.noAttendance') }}</MutedText>
  </div>
</template>

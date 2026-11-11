/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import {type AdminStatistics, StatisticsWindow} from '@/api/generated/schema'
import WindowField from '../admintrafficview/WindowField.vue'
import UsageStatsSection from './UsageStatsSection.vue'
import PeopleStatsSection from './PeopleStatsSection.vue'
import GrowthStatsSection from './GrowthStatsSection.vue'
import AttendanceStatsSection from './AttendanceStatsSection.vue'
import FederationStatsSection from './FederationStatsSection.vue'

/**
 * The sections of the statistics page that follow the chosen window: usage, people, growth,
 * attendance and federation, below the field that chooses it. The figures stay on screen while a
 * new window loads; the spinner beside the field says that one is on its way.
 */
defineProps<{
  stats: AdminStatistics
  loading: boolean
}>()

const statisticsWindow = defineModel<StatisticsWindow>({required: true})

const {t} = useI18n()

const WINDOWS = [StatisticsWindow.DAYS_30, StatisticsWindow.MONTHS_12, StatisticsWindow.ALL] as const
const windowOptions = computed(() => WINDOWS.map(w => ({value: w, label: t(`adminStats.statisticsWindow.${w}`)})))
const windowModel = computed({
  get: () => statisticsWindow.value,
  set: v => statisticsWindow.value = v as StatisticsWindow,
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end gap-3">
      <WindowField v-model="windowModel" :options="windowOptions"/>
      <Spinner v-if="loading" size="sm"/>
    </div>
    <UsageStatsSection :usage="stats.activeByDay" :by-week="stats.window !== StatisticsWindow.DAYS_30"/>
    <PeopleStatsSection :people="stats.peopleByType"/>
    <GrowthStatsSection :growth="stats.growth"/>
    <AttendanceStatsSection :months="stats.attendanceByMonth"/>
    <FederationStatsSection :federation="stats.federation"/>
  </div>
</template>

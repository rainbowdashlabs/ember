/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatTile from '@/components/statistic/StatTile.vue'

const props = defineProps<{
  totalEvents: number
  eventsUpcoming: number
  totalEventRegistrations: number
  totalAttendanceSessions: number
  sessionsThisMonth: number
  totalAttendanceEntries: number
  totalInventoryItems: number
  totalProfileFields: number
}>()

const {t} = useI18n()

const tiles = computed(() => [
  {key: 'events', value: props.totalEvents},
  {key: 'eventsUpcoming', value: props.eventsUpcoming},
  {key: 'eventRegistrations', value: props.totalEventRegistrations},
  {key: 'attendanceSessions', value: props.totalAttendanceSessions},
  {key: 'sessionsThisMonth', value: props.sessionsThisMonth},
  {key: 'attendanceEntries', value: props.totalAttendanceEntries},
  {key: 'inventoryItems', value: props.totalInventoryItems},
  {key: 'profileFields', value: props.totalProfileFields},
])
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.dataSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <StatTile v-for="tile in tiles" :key="tile.key" :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
    </div>
  </div>
</template>

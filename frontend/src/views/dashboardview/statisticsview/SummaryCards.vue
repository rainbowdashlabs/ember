/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StatValue from '@/components/typography/StatValue.vue'

interface StatsData {
  memberCount: number
  groupCounts: Record<string, number>
  attendanceByMonth: Array<{ month: string; sessions: number; present: number; absent: number; declined: number }>
  inventoryStatus: Array<{ name: string; total: number; assigned: number; lost: number }>
  eventRegistrations: Array<{ name: string; accepted: number; pending: number; declined: number }>
  roleCounts: Record<string, number>
}

const props = defineProps<{
  stats: StatsData
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
    <NeutralContainer class="text-center">
      <StatValue>{{ props.stats.memberCount }}</StatValue>
      <p class="text-sm text-(--text-muted)">{{ t('statistics.members') }}</p>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue>{{ Object.keys(props.stats.groupCounts).length }}</StatValue>
      <p class="text-sm text-(--text-muted)">{{ t('statistics.groups') }}</p>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue>{{ props.stats.attendanceByMonth.reduce((s, a) => s + a.sessions, 0) }}</StatValue>
      <p class="text-sm text-(--text-muted)">{{ t('statistics.totalSessions') }}</p>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue>{{ props.stats.inventoryStatus.reduce((s, i) => s + i.total, 0) }}</StatValue>
      <p class="text-sm text-(--text-muted)">{{ t('statistics.totalItems') }}</p>
    </NeutralContainer>
  </div>
</template>

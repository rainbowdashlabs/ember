/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DataTable from '@/components/table/DataTable.vue'
import type {SessionMemberEntry} from '@/api/generated/schema'
import {formatDayMonth, formatTime} from '@/util/format'
import {extraTimeText} from '@/components/attendance/timerules/extraTimeText'

const {t} = useI18n()

const props = defineProps<{
  entries: SessionMemberEntry[]
  /** Whether the sheet ran over more than one day, where a time alone names two moments. */
  spansDays?: boolean
}>()

function moment(value?: string | null): string {
  if (!value) return ''
  return props.spansDays ? `${formatDayMonth(value)} ${formatTime(value)}` : formatTime(value)
}
</script>

<template>
  <DataTable plain>
    <template #head>
      <th class="text-left py-1.5 px-2">{{ t('attendanceReport.name') }}</th>
      <th class="text-center py-1.5 px-2">{{ t('attendanceReport.from') }}</th>
      <th class="text-center py-1.5 px-2">{{ t('attendanceReport.to') }}</th>
      <th class="text-right py-1.5 px-2">{{ t('attendanceReport.hours') }}</th>
    </template>
    <tr v-for="entry in entries" :key="entry.memberId"
        class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
      <td class="py-1.5 px-2">
        <span>{{ entry.name }}</span>
        <span
            v-if="entry.preparationMinutes > 0 || entry.followUpMinutes > 0"
            class="ml-2 text-xs text-(--text-muted)"
            data-testid="report-extra-time"
        >{{ extraTimeText(t, entry.preparationMinutes, entry.followUpMinutes) }}</span>
      </td>
      <td class="text-center py-1.5 px-2">{{ moment(entry.checkIn) }}</td>
      <td class="text-center py-1.5 px-2">{{ moment(entry.checkOut) }}</td>
      <td class="text-right py-1.5 px-2 font-mono">{{ entry.hours.toFixed(1) }}</td>
    </tr>
  </DataTable>
</template>

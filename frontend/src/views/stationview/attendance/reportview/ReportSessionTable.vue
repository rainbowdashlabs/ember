/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import THead from '@/components/table/THead.vue'
import type {SessionMemberEntry} from '@/api/attendance'

const {t} = useI18n()

defineProps<{
  entries: SessionMemberEntry[]
}>()
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
      <THead>
        <th class="text-left py-1.5 px-2">{{ t('attendanceReport.name') }}</th>
        <th class="text-center py-1.5 px-2">{{ t('attendanceReport.from') }}</th>
        <th class="text-center py-1.5 px-2">{{ t('attendanceReport.to') }}</th>
        <th class="text-right py-1.5 px-2">{{ t('attendanceReport.hours') }}</th>
      </THead>
      </thead>
      <tbody>
      <tr v-for="entry in entries" :key="entry.memberId"
          class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
        <td class="py-1.5 px-2">{{ entry.name }}</td>
        <td class="text-center py-1.5 px-2">{{ entry.checkIn }}</td>
        <td class="text-center py-1.5 px-2">{{ entry.checkOut }}</td>
        <td class="text-right py-1.5 px-2 font-mono">{{ entry.hours.toFixed(1) }}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

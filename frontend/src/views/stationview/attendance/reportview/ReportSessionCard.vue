/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ReportSessionTable from './ReportSessionTable.vue'
import type {SessionData} from '@/api/attendance'
import {formatDate, formatTime} from '@/util/format'

const {t} = useI18n()

defineProps<{
  session: SessionData
}>()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center gap-2 flex-wrap">
      <span class="font-medium">{{ session.title }}</span>
      <span class="text-sm text-(--text-muted)">{{ formatDate(session.date) }} · {{ formatTime(session.startTime) }} – {{ formatTime(session.endTime) }}</span>
    </div>
    <div class="text-xs text-(--text-muted)">
      {{ t('attendanceReport.presentOfExpected', {present: session.presentCount, expected: session.expectedCount}) }}
    </div>
    <ReportSessionTable :entries="session.entries"/>
  </NeutralContainer>
</template>

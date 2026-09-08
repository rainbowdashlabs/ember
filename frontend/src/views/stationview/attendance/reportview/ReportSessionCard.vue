/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ReportSessionTable from './ReportSessionTable.vue'
import type {SessionData} from '@/api/attendance'
import {formatDate, formatTime} from '@/util/format'

/**
 * One sheet in the report, with the times it ran to and, where somebody set one, what it counts as.
 *
 * <p>A sheet over several days names both of its days, and a sheet whose hours were set by hand says
 * so, because the column beside it will not follow the times otherwise.
 */
const {t} = useI18n()

const props = defineProps<{
  session: SessionData
}>()

const spansDays = computed(() => !!props.session.endDate && props.session.endDate !== props.session.date)

const when = computed(() => spansDays.value
    ? `${formatDate(props.session.date)} ${formatTime(props.session.startTime)}`
        + ` – ${formatDate(props.session.endDate)} ${formatTime(props.session.endTime)}`
    : `${formatDate(props.session.date)} · ${formatTime(props.session.startTime)}`
        + ` – ${formatTime(props.session.endTime)}`)
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center gap-2 flex-wrap">
      <span class="font-medium">{{ session.title }}</span>
      <span class="text-sm text-(--text-muted)">{{ when }}</span>
      <span v-if="session.countedHours != null" class="text-xs text-(--text-muted)">
        {{ t('attendanceReport.countedAs', {hours: session.countedHours}) }}
      </span>
    </div>
    <div class="text-xs text-(--text-muted)">
      {{ t('attendanceReport.presentOfExpected', {present: session.presentCount, expected: session.expectedCount}) }}
    </div>
    <ReportSessionTable :entries="session.entries" :spans-days="spansDays"/>
  </NeutralContainer>
</template>

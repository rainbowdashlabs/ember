/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {formatDate} from '@/util/format'

/**
 * When an appointment falls, written above its name.
 *
 * <p>A list of appointments is read by date before it is read by name: somebody scanning it wants to
 * know what is on Saturday, not where the drill is. One that runs over more than one day says both
 * ends and carries the calendar icon, because a single date would be a lie about it.
 */
defineProps<{
  date: string
  endDate: string | null
  startTime?: string
  endTime?: string
  formatTime: (iso?: string) => string
}>()

const {t} = useI18n()

/** The weekday of a plain calendar date, read in the station's own words. */
function dayLabel(date: string): string {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString('de-DE', {weekday: 'long', timeZone: 'UTC'})
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-2 text-sm" data-testid="upcoming-event-when">
    <span v-if="endDate" :title="t('eventsUpcoming.multiDay')" class="inline-block">
      <MutedIcon :icon="['fas', 'calendar-days']"/>
    </span>
    <MutedText size="sm">
      <template v-if="endDate">{{ dayLabel(date) }}, {{ formatDate(date) }} – {{ dayLabel(endDate) }}, {{ formatDate(endDate) }}</template>
      <template v-else>{{ dayLabel(date) }}, {{ formatDate(date) }}</template>
    </MutedText>
    <MutedText>{{ formatTime(startTime) }} – {{ formatTime(endTime) }}</MutedText>
  </div>
</template>

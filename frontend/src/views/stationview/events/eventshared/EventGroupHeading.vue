/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import type {RouteLocationRaw} from 'vue-router'
import {useSession} from '@/composables/useSession'
import {formatDate, formatDayMonth} from '@/util/format'
import {contrastTextColor} from '@/util/contrastColor'
import {eventGroupColor, type EventGroupMark} from './eventGroupColor'

/**
 * The bar along the top edge of an appointment of an event group, in the group's colour, that says
 * which event group it belongs to, which part it is and over which days the whole event group runs
 * ("Jugendflamme 1", "Teil 2 von 4", "12.12. bis 20.12.2026"), so the appointment does not read as one
 * on its own. Together with the {@link EventGroupBand} on the left edge it frames the appointment; the
 * row or tile it sits in must be positioned (`relative`) and leave room at its top. On the event group's
 * own page there is no part to name, and an event group without parts has no days yet.
 */
const props = defineProps<{
  eventGroup: EventGroupMark
  /** Where the event group is opened from the bar; left out where the bar sits inside a link already. */
  to?: RouteLocationRaw
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()

const color = computed(() => eventGroupColor(props.eventGroup.eventGroupId, props.eventGroup.color))
const textColor = computed(() => contrastTextColor(color.value))

/**
 * Rounds the inner corner where the bar meets the band, the way the tile's outer corner is rounded:
 * the group's colour fills the corner outside a quarter circle of the theme's radius, and the tile shows
 * through inside it, whatever its own background.
 */
const innerCorner = computed(() => ({
  width: 'var(--radius-theme)',
  height: 'var(--radius-theme)',
  background: `radial-gradient(circle at 100% 100%, transparent calc(var(--radius-theme) - 0.5px), ${color.value} var(--radius-theme))`,
}))
const span = computed(() => {
  if (!props.eventGroup.firstStart || !props.eventGroup.lastEnd) return ''
  const first = formatDate(props.eventGroup.firstStart, stationTimezone.value)
  const last = formatDate(props.eventGroup.lastEnd, stationTimezone.value)
  if (first === last) return first
  return t('eventGroups.dateRange', {first: formatDayMonth(props.eventGroup.firstStart, stationTimezone.value), last})
})
</script>

<template>
  <p
      class="absolute -inset-x-px -top-px m-0 flex h-6 items-center gap-2 rounded-t-theme pl-5 pr-3 text-xs whitespace-nowrap"
      :style="{backgroundColor: color, color: textColor}"
      data-testid="event-group-heading">
    <span aria-hidden="true" class="pointer-events-none absolute top-full left-1.5" :style="innerCorner" data-testid="event-group-inner-corner"/>
    <AppIcon :icon="['fas', 'layer-group']" class="shrink-0"/>
    <span class="min-w-0 truncate font-semibold">{{ eventGroup.eventGroupName }}</span>
    <span v-if="eventGroup.number != null" class="shrink-0">{{ t('eventGroups.partNumber', {number: eventGroup.number, count: eventGroup.count}) }}</span>
    <span v-if="span" class="shrink-0">{{ span }}</span>
    <NuxtLink v-if="to" :to="to" class="row-link ml-auto shrink-0 font-semibold underline" :style="{color: textColor}" data-testid="event-group-heading-link">
      {{ t('eventGroups.openEventGroup') }}
    </NuxtLink>
  </p>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import MutedText from '@/components/typography/MutedText.vue'
import EventGroupBand from './EventGroupBand.vue'
import EventGroupHeading from './EventGroupHeading.vue'
import type {EventGroupMark} from './eventGroupColor'

/**
 * Frames a page that belongs to an event group, the page of one of its appointments or its own, in the
 * event group's colour: the band down the left edge and the bar along the top that names it, so the
 * page reads as a part of the event group the way its rows in the lists do. Without an event group the
 * content stands as it is.
 */
defineProps<{
  eventGroup: EventGroupMark | null | undefined
  /** What the frame says under the bar, such as where the sign-up lives. */
  message?: string
  /** Where the bar opens the event group; left out on the event group's own page. */
  to?: RouteLocationRaw
}>()
</script>

<template>
  <div v-if="eventGroup" class="relative space-y-4 rounded-theme border border-transparent pl-5 pt-9" data-testid="event-group-frame">
    <EventGroupBand :event-group="eventGroup"/>
    <EventGroupHeading :event-group="eventGroup" :to="to"/>
    <MutedText v-if="message" tag="p" size="sm">{{ message }}</MutedText>
    <slot/>
  </div>
  <slot v-else/>
</template>

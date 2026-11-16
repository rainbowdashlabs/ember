/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EventGroupBand from './EventGroupBand.vue'
import EventGroupHeading from './EventGroupHeading.vue'
import type {EventGroupMark} from './eventGroupColor'

/**
 * The tile an appointment or an event group sits in. A part of an event group gets the group's band
 * on its left edge and its heading along the top, with room made for both, the same on every list and
 * on the event group's own page; anything else is a plain tile. Classes and attributes given to the
 * tile reach its container.
 */
defineProps<{
  /** The event group the tile belongs to, or null for an appointment of its own. */
  eventGroup?: EventGroupMark | null
}>()
</script>

<template>
  <NeutralContainer :class="{'relative pl-5 pt-9': eventGroup}">
    <template v-if="eventGroup">
      <EventGroupBand :event-group="eventGroup"/>
      <EventGroupHeading :event-group="eventGroup"/>
    </template>
    <slot/>
  </NeutralContainer>
</template>

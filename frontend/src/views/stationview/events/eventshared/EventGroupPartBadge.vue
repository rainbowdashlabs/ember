/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import type {EventGroupPartSummary} from '@/api/generated/schema'

/**
 * Says which event group an appointment is a part of: "Jugendflamme 1, part 2 of 4". Where it stands on its
 * own it links to the event group; inside a row that is a link itself it is only the badge.
 */
withDefaults(defineProps<{
  eventGroup: EventGroupPartSummary
  link?: boolean
}>(), {
  link: false,
})

const {t} = useI18n()
</script>

<template>
  <RowLink v-if="link" :to="{name: 'event-group', params: {eventGroupId: eventGroup.eventGroupId}}" class="inline-flex">
    <SecondaryBadge data-testid="event-group-part-badge">
      <font-awesome-icon :icon="['fas', 'graduation-cap']" class="mr-1 h-3 w-3"/>
      {{ t('eventGroups.badge', {eventGroup: eventGroup.eventGroupName, number: eventGroup.number, count: eventGroup.count}) }}
    </SecondaryBadge>
  </RowLink>
  <SecondaryBadge v-else data-testid="event-group-part-badge">
    <font-awesome-icon :icon="['fas', 'graduation-cap']" class="mr-1 h-3 w-3"/>
    {{ t('eventGroups.badge', {eventGroup: eventGroup.eventGroupName, number: eventGroup.number, count: eventGroup.count}) }}
  </SecondaryBadge>
</template>

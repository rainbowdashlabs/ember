/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {EventGroupPartSummary} from '@/api/generated/schema'

/** Says which event group an appointment is a part of: "Jugendflamme 1, part 2 of 4". */
defineProps<{
  eventGroup: EventGroupPartSummary
}>()

const {t} = useI18n()
</script>

<template>
  <SecondaryBadge data-testid="event-group-part-badge">
    <font-awesome-icon :icon="['fas', 'graduation-cap']" class="mr-1 h-3 w-3"/>
    {{ t('eventGroups.badge', {eventGroup: eventGroup.eventGroupName, number: eventGroup.number, count: eventGroup.count}) }}
  </SecondaryBadge>
</template>

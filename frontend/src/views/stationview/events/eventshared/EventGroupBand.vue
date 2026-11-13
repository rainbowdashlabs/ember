/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {EventGroupPartSummary} from '@/api/generated/schema'
import {eventGroupColor} from './eventGroupColor'

/**
 * Marks an appointment as a part of an event group with the group's colour. As `edge` it is a band
 * on the left edge of the row or tile it sits in, which must be positioned (`relative`); as `mark` it
 * is a short bar in a line of text, for a calendar chip too narrow for a band. Either way it names the
 * event group and the part ("Jugendflamme 1, Teil 2 von 4") as its label and tooltip, so nothing is
 * said by colour alone.
 */
const props = withDefaults(defineProps<{
  eventGroup: EventGroupPartSummary
  variant?: 'edge' | 'mark'
}>(), {
  variant: 'edge',
})

const {t} = useI18n()

const label = computed(() => t('eventGroups.badge', {
  eventGroup: props.eventGroup.eventGroupName,
  number: props.eventGroup.number,
  count: props.eventGroup.count,
}))
const color = computed(() => eventGroupColor(props.eventGroup.eventGroupId, props.eventGroup.color))
</script>

<template>
  <span
      v-if="variant === 'edge'"
      role="img"
      :aria-label="label"
      :title="label"
      class="absolute inset-y-0 left-0 w-1.5"
      :style="{backgroundColor: color, borderTopLeftRadius: 'inherit', borderBottomLeftRadius: 'inherit'}"
      data-testid="event-group-band"
  />
  <span
      v-else
      role="img"
      :aria-label="label"
      :title="label"
      class="inline-block w-1 h-[0.9em] mr-0.5 align-[-0.1em] rounded-sm"
      :style="{backgroundColor: color}"
      data-testid="event-group-band"
  />
</template>

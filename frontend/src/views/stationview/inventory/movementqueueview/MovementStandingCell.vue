/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {toRef} from 'vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {useMovementRowView} from './movementRowView'
import type {Movement} from '@/api/movements'

/**
 * Where a movement stands: the last step whose words are already true and who is being waited on,
 * or how it ended.
 *
 * <p>No status anywhere. A step is named after the state it brings about, so the step a movement
 * waits on has not happened yet and wearing its label would say a piece had been taken in while it
 * is still on the member. That step is on the row's button instead.
 */
const props = defineProps<{
  movement: Movement
}>()

const {open, standing} = useMovementRowView(toRef(props, 'movement'))
</script>

<template>
  <span class="inline-flex flex-wrap items-center gap-1">
    <template v-if="open">
      <InfoBadge v-if="props.movement.reachedStepLabel" data-testid="movement-step">
        {{ props.movement.reachedStepLabel }}
      </InfoBadge>
      <SecondaryBadge v-if="standing" data-testid="movement-turn">{{ standing }}</SecondaryBadge>
    </template>
    <SecondaryBadge v-else data-testid="movement-state">{{ standing }}</SecondaryBadge>
  </span>
</template>

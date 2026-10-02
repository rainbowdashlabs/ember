/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {toRef} from 'vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {useMovementStanding, type StandingOf} from '@/composables/useMovementStanding'

/**
 * Where a movement stands: the last step whose words are already true and who is being waited on,
 * or how it ended.
 *
 * <p>The same badges on a movement's row and on the piece of gear it runs on, so the two cannot drift
 * apart. The step being waited on is never shown here: it has not happened yet, and wearing its label
 * would say a piece had been taken in while it is still on the member.
 */
const props = defineProps<{
  movement: StandingOf
}>()

const {open, reached, standing} = useMovementStanding(toRef(props, 'movement'))
</script>

<template>
  <span class="inline-flex flex-wrap items-center gap-1">
    <template v-if="open">
      <InfoBadge v-if="reached" data-testid="movement-step">{{ reached }}</InfoBadge>
      <SecondaryBadge v-if="standing" data-testid="movement-turn">{{ standing }}</SecondaryBadge>
    </template>
    <SecondaryBadge v-else data-testid="movement-state">{{ standing }}</SecondaryBadge>
  </span>
</template>

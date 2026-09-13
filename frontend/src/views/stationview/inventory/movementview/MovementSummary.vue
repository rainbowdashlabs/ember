/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MovementPieces from './MovementPieces.vue'
import MovementFacts from './MovementFacts.vue'
import type {Movement} from '@/api/movements'

/**
 * What a movement is about, above the chain it walks: the two pieces and their owner first, then
 * what was written down about it.
 */
const props = defineProps<{movement: Movement}>()
</script>

<template>
  <NeutralContainer class="space-y-2 text-sm">
    <MovementPieces :movement="props.movement"/>
    <MovementFacts :movement="props.movement"/>
  </NeutralContainer>
</template>

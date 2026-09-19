/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {toRef} from 'vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {useMovementRowView} from './movementRowView'
import type {Movement} from '@/api/movements'

/**
 * What a movement is about: the piece where there is one, the inventory otherwise. It carries the
 * movement's id, which is what the end-to-end stories find a row by.
 */
const props = defineProps<{
  movement: Movement
}>()

const {chip} = useMovementRowView(toRef(props, 'movement'))
</script>

<template>
  <div :data-movement="props.movement.id" class="min-w-0">
    <ItemChip :source="chip"/>
  </div>
</template>

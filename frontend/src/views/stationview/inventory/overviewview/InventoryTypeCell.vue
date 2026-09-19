/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {inventoryTypeBadge} from '@/util/inventoryType'

/** The kind of inventory a row comes out of, as the badge that kind wears everywhere. */
const props = defineProps<{
  type?: string | null
  /** The kind in words, as the table already has it. */
  text: string
}>()
</script>

<template>
  <component :is="inventoryTypeBadge(props.type)" v-if="props.text">{{ props.text }}</component>
</template>

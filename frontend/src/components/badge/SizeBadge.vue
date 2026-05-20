/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

defineProps<{
  lost?: boolean
}>()
</script>

<template>
  <BaseBadge :class="lost ? 'bg-error/15 text-error' : 'bg-secondary-accent/15 text-secondary-accent'">
    <slot/>
  </BaseBadge>
</template>

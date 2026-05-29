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
  <BaseBadge :bg-class="lost ? 'bg-error/70' : 'bg-secondary-accent/70'">
    <slot/>
  </BaseBadge>
</template>

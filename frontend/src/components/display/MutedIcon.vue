/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  icon: string[]
  size?: 'xs' | 'sm' | 'md'
}>()
</script>

<template>
  <font-awesome-icon
      :icon="icon"
      :class="{
        'h-2.5 w-2.5': size === 'xs',
        'h-3 w-3': !size || size === 'sm',
        'h-3.5 w-3.5': size === 'md',
      }"
      class="text-(--text-muted)"
  />
</template>

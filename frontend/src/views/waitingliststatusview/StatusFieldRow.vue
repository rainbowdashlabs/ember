/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
const props = defineProps<{
  label: string
  value?: string | number | null
  wide?: boolean
}>()
</script>

<template>
  <div class="text-sm" :class="{ 'sm:col-span-2': props.wide }">
    <span class="text-(--text-muted)">{{ props.label }}:</span>
    <template v-if="$slots.default">
      <slot />
    </template>
    <span v-else class="ml-1 font-medium">{{ props.value ?? '-' }}</span>
  </div>
</template>

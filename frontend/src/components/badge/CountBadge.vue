/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * How many things wait for attention behind an entry of the sidebar, as a red dot with the number.
 */
defineProps<{
  count: number
  /** The smaller dot of an entry one level down. */
  small?: boolean
}>()
</script>

<template>
  <span
      class="inline-flex items-center justify-center rounded-full font-bold bg-error text-error-text"
      :class="small ? 'min-w-4 h-4 px-1 text-[10px]' : 'min-w-5 h-5 px-1.5 text-xs'"
  >{{ count }}</span>
</template>

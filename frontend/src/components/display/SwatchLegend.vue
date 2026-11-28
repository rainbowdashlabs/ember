/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/** One entry of a legend: what a look stands for, and the classes that draw a sample of it. */
export interface SwatchEntry {
  label: string
  swatch: string
  /** Whether the sample is hatched, as blocked days are drawn. */
  hatched?: boolean
}

/** A row of small samples, each beside what it stands for, under a calendar or a chart. */
defineProps<{
  entries: SwatchEntry[]
}>()
</script>

<template>
  <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs">
    <li v-for="entry in entries" :key="entry.label" class="flex items-center gap-1">
      <span class="inline-block h-3 w-4 rounded-sm" :class="[entry.swatch, entry.hatched ? 'swatch-hatched' : '']" aria-hidden="true"/>
      {{ entry.label }}
    </li>
  </ul>
</template>

<style scoped>
.swatch-hatched {
  background-image: repeating-linear-gradient(45deg, var(--border) 0 2px, transparent 2px 4px);
}
</style>

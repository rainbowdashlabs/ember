/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {PitchMetric} from './pitchTypes'
import {accentText} from './pitchAccents'

defineProps<{
  metrics: PitchMetric[]
}>()
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
    <div v-for="metric in metrics" :key="metric.label"
         class="rounded-theme border border-bg-light-accent bg-(--bg) px-4 py-3 dark:border-bg-dark-accent">
      <p class="text-2xl font-extrabold" :class="accentText(metric.accent)">{{ metric.value }}</p>
      <p class="text-xs text-(--text-muted)">{{ metric.label }}</p>
    </div>
  </div>
</template>

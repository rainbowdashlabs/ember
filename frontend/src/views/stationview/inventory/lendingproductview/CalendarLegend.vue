/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

/** What the colours of the availability calendar stand for. */
const {t} = useI18n()

const entries = computed(() => [
  {label: t('lendingCalendar.allFree'), swatch: 'bg-success/25'},
  {label: t('lendingCalendar.someFree'), swatch: 'bg-info/30'},
  {label: t('lendingCalendar.noneFree'), swatch: 'bg-bg-light-accent dark:bg-bg-dark-accent'},
  {label: t('lendingCalendar.blocked'), swatch: 'legend-blocked'},
  {label: t('lendingCalendar.chosen'), swatch: 'ring-2 ring-primary'},
])
</script>

<template>
  <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs">
    <li v-for="entry in entries" :key="entry.label" class="flex items-center gap-1">
      <span class="inline-block h-3 w-3 rounded-sm" :class="entry.swatch" aria-hidden="true"/>
      {{ entry.label }}
    </li>
  </ul>
</template>

<style scoped>
.legend-blocked {
  background-image: repeating-linear-gradient(45deg, var(--border) 0 2px, transparent 2px 4px);
}
</style>

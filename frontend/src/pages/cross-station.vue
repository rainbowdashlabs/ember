/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import CrossStationDashboardView from '~/views/CrossStationDashboardView.vue'

definePageMeta({
  layout: 'default',
  name: 'cross-station-dashboard',
})

useHead({
  title: 'Übersicht',
})
</script>

<template>
  <CrossStationDashboardView />
</template>

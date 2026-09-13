/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MovementQueueView from '~/views/stationview/inventory/MovementQueueView.vue'

definePageMeta({
  layout: 'station',
  name: 'inventory-movements',
})
</script>

<template>
  <MovementQueueView />
</template>

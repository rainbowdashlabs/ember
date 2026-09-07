/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import BorrowedGearView from '~/views/stationview/inventory/BorrowedGearView.vue'

definePageMeta({
  layout: 'station',
  name: 'inventory-borrowed',
})
</script>

<template>
  <BorrowedGearView />
</template>

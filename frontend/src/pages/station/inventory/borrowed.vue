/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * The partner gear at the station is shown on the lending overview now. This keeps the address of the
 * former borrowed gear page working, because somebody has it bookmarked.
 */
definePageMeta({
  layout: 'station',
  name: 'inventory-borrowed-legacy',
  redirect: '/station/inventory/lending',
})
</script>

<template>
  <div/>
</template>

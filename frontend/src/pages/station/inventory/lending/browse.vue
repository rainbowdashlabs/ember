/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * Partner gear is found in the lending shop now. This keeps the address of the former browse page
 * working, because somebody has it bookmarked.
 */
definePageMeta({
  layout: 'station',
  name: 'inventory-lending-browse-legacy',
  redirect: '/station/inventory/lending/shop',
})
</script>

<template>
  <div/>
</template>

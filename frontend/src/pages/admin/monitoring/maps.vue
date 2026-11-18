/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * The map settings moved out of the monitoring pages into the settings. This keeps the address they used to live
 * at working, because somebody has it bookmarked.
 */
definePageMeta({
  layout: 'admin',
  name: 'admin-maps-legacy',
  redirect: '/admin/settings/maps',
})
</script>

<template>
  <div/>
</template>

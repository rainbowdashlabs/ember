/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
definePageMeta({
  layout: 'station',
})

if (import.meta.client) {
  navigateTo('/station/dashboard/overview', {replace: true})
}
</script>

<template>
  <div />
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import StationSelectView from '~/views/StationSelectView'

definePageMeta({
  layout: 'default',
  name: 'station-select',
})

useHead({
  title: 'Station wählen',
})
</script>

<template>
  <StationSelectView />
</template>

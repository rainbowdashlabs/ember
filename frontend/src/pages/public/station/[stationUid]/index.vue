/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
const route = useRoute()

definePageMeta({
  layout: 'public-station',
  name: 'public-station',
})

navigateTo(`/public/station/${route.params.stationUid}/calendar`, {replace: true})
</script>

<template>
  <div />
</template>

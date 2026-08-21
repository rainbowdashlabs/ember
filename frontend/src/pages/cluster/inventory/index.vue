/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ClusterInventoryView from '~/views/clusterview/ClusterInventoryView.vue'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-inventory',
})
</script>

<template>
  <ClusterInventoryView />
</template>

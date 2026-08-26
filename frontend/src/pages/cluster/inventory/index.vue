/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ClusterInventoryView from '~/views/clusterview/ClusterInventoryView.vue'
import {CLUSTER_INVENTORY_ROUTES} from '~/views/clusterview/clusterinventoryview/clusterInventoryRoutes'
import {provideInventoryRoutes} from '~/composables/useInventoryRoutes'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-inventory',
})

provideInventoryRoutes(CLUSTER_INVENTORY_ROUTES)
</script>

<template>
  <ClusterInventoryView />
</template>

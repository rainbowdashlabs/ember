/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ItemDetailView from '~/views/stationview/inventory/ItemDetailView.vue'
import {CLUSTER_INVENTORY_ROUTES} from '~/views/clusterview/clusterinventoryview/clusterInventoryRoutes'
import {provideInventoryRoutes} from '~/composables/useInventoryRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-inventory-item',
})

provideInventoryRoutes(CLUSTER_INVENTORY_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <ItemDetailView v-if="homeStationId" />
</template>

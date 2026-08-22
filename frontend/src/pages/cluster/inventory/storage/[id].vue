/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ContainerDetailView from '~/views/stationview/inventory/ContainerDetailView.vue'
import {CLUSTER_INVENTORY_ROUTES} from '~/views/clusterview/clusterinventoryview/clusterInventoryRoutes'
import {provideInventoryRoutes} from '~/composables/useInventoryRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-inventory-container',
})

provideInventoryRoutes(CLUSTER_INVENTORY_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <ContainerDetailView v-if="homeStationId" />
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import RequirementsView from '~/views/stationview/inventory/RequirementsView.vue'
import InventoryTabs from '~/views/clusterview/clusterinventoryview/InventoryTabs.vue'
import {CLUSTER_INVENTORY_ROUTES} from '~/views/clusterview/clusterinventoryview/clusterInventoryRoutes'
import {provideInventoryRoutes} from '~/composables/useInventoryRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-inventory-requirements',
})

provideInventoryRoutes(CLUSTER_INVENTORY_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <RequirementsView v-if="homeStationId" station-scoped>
    <template #before>
      <InventoryTabs/>
    </template>
  </RequirementsView>
</template>

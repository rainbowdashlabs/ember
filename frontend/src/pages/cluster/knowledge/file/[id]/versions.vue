/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import KbVersionsView from '~/views/stationview/knowledge/KbVersionsView.vue'
import {CLUSTER_KB_ROUTES} from '~/views/clusterview/clusterknowledgeview/clusterKbRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-kb-versions',
})

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <KbVersionsView v-if="homeStationId" :routes="CLUSTER_KB_ROUTES" />
</template>

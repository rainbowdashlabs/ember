/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import KbFileView from '~/views/stationview/knowledge/KbFileView.vue'
import {CLUSTER_KB_ROUTES} from '~/views/clusterview/clusterknowledgeview/clusterKbRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-kb-file',
})

const route = useRoute()
const {homeStationId} = useClusterHomeStation()

const fileId = computed(() => Number(route.params.id))
</script>

<template>
  <KbFileView
      v-if="homeStationId"
      :file-id="fileId"
      :routes="CLUSTER_KB_ROUTES"
  />
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ClusterMediaView from '~/views/clusterview/ClusterMediaView.vue'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-media',
})

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <ClusterMediaView v-if="homeStationId" />
</template>

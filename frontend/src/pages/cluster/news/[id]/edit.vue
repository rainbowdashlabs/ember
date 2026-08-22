/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import EditView from '~/views/stationview/news/EditView.vue'
import {CLUSTER_NEWS_ROUTES} from '~/views/clusterview/clusternewsview/clusterNewsRoutes'
import {provideNewsRoutes} from '~/composables/useNewsRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-news-edit',
})

provideNewsRoutes(CLUSTER_NEWS_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <EditView v-if="homeStationId" />
</template>

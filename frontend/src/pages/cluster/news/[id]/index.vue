/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DetailView from '~/views/stationview/news/DetailView.vue'
import {CLUSTER_NEWS_ROUTES} from '~/views/clusterview/clusternewsview/clusterNewsRoutes'
import {provideNewsRoutes} from '~/composables/useNewsRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-news-detail',
})

provideNewsRoutes(CLUSTER_NEWS_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <DetailView v-if="homeStationId" />
</template>

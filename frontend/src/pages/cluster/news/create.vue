/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import EditView from '~/views/stationview/news/EditView.vue'
import {CLUSTER_NEWS_ROUTES} from '~/views/clusterview/clusternewsview/clusterNewsRoutes'
import {CLUSTER_EVENT_ROUTES} from '~/views/clusterview/clustereventsview/clusterEventRoutes'
import {provideNewsRoutes} from '~/composables/useNewsRoutes'
import {provideEventRoutes} from '~/composables/useEventRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-news-create',
})

provideNewsRoutes(CLUSTER_NEWS_ROUTES)
// An entry written from an appointment links back to it, and the association's calendar lives at
// its own addresses. Without this the link would point into the station panel.
provideEventRoutes(CLUSTER_EVENT_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <EditView v-if="homeStationId" />
</template>

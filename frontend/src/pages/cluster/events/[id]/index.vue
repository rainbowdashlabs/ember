/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import EventDetailView from '~/views/stationview/events/EventDetailView.vue'
import {CLUSTER_EVENT_ROUTES} from '~/views/clusterview/clustereventsview/clusterEventRoutes'
import {CLUSTER_NEWS_ROUTES} from '~/views/clusterview/clusternewsview/clusterNewsRoutes'
import {provideEventRoutes} from '~/composables/useEventRoutes'
import {provideNewsRoutes} from '~/composables/useNewsRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-event-detail',
})

provideEventRoutes(CLUSTER_EVENT_ROUTES)
// Announcing an appointment leads into the news screens, and the association keeps its own. Without
// this the button would drop somebody who never left the association into the station panel.
provideNewsRoutes(CLUSTER_NEWS_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <EventDetailView v-if="homeStationId" />
</template>

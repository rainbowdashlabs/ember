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

/**
 * An association's appointment. The news routes are the association's own too, so announcing the
 * appointment never drops somebody into the station panel.
 */
definePageMeta({
  layout: 'cluster',
  name: 'cluster-event-detail',
})

provideEventRoutes(CLUSTER_EVENT_ROUTES)
provideNewsRoutes(CLUSTER_NEWS_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <EventDetailView v-if="homeStationId" />
</template>

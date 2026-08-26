/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import EventDetailView from '~/views/stationview/events/EventDetailView.vue'
import {CLUSTER_EVENT_ROUTES} from '~/views/clusterview/clustereventsview/clusterEventRoutes'
import {provideEventRoutes} from '~/composables/useEventRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-event-detail',
})

provideEventRoutes(CLUSTER_EVENT_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <EventDetailView v-if="homeStationId" />
</template>

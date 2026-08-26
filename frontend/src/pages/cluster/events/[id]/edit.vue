/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import EventEditView from '~/views/stationview/events/EventEditView.vue'
import {CLUSTER_EVENT_ROUTES} from '~/views/clusterview/clustereventsview/clusterEventRoutes'
import {provideEventRoutes} from '~/composables/useEventRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-event-edit',
})

provideEventRoutes(CLUSTER_EVENT_ROUTES)

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <EventEditView v-if="homeStationId" />
</template>

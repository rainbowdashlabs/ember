/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IndexView from '~/views/stationview/events/IndexView.vue'
import {CLUSTER_EVENT_ROUTES} from '~/views/clusterview/clustereventsview/clusterEventRoutes'
import {provideEventRoutes} from '~/composables/useEventRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-events',
})

provideEventRoutes(CLUSTER_EVENT_ROUTES)

const {homeStationId} = useClusterHomeStation()

const {t} = useI18n()
</script>

<template>
  <IndexView v-if="homeStationId"
      :subtitle="t('pages.cluster-events.subtitle')"
      :title="t('pages.cluster-events.title')" />
</template>

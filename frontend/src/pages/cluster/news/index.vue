/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ListView from '~/views/stationview/news/ListView.vue'
import {CLUSTER_NEWS_ROUTES} from '~/views/clusterview/clusternewsview/clusterNewsRoutes'
import {provideNewsRoutes} from '~/composables/useNewsRoutes'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-news',
})

provideNewsRoutes(CLUSTER_NEWS_ROUTES)

const {homeStationId} = useClusterHomeStation()

const {t} = useI18n()
</script>

<template>
  <ListView v-if="homeStationId"
      :subtitle="t('pages.cluster-news.subtitle')"
      :title="t('pages.cluster-news.title')" />
</template>

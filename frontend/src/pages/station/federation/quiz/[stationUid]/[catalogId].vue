/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import FederatedCatalogView from '~/views/stationview/quiz/FederatedCatalogView.vue'

definePageMeta({
  layout: 'station',
  name: 'federated-quiz-catalog',
})

const route = useRoute()

const stationUid = computed(() => String(route.params.stationUid))
const catalogId = computed(() => Number(route.params.catalogId))
</script>

<template>
  <FederatedCatalogView :station-uid="stationUid" :catalog-id="catalogId" />
</template>

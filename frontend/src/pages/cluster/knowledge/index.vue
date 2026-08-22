/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ClusterKnowledgeView from '~/views/clusterview/ClusterKnowledgeView.vue'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-knowledge',
})

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <ClusterKnowledgeView v-if="homeStationId" />
</template>

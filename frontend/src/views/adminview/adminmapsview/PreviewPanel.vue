/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StationMap from '@/components/map/StationMap.vue'

const previewStations = computed(() => [
  {uid: 'preview', name: 'Preview', latitude: 51.0, longitude: 10.0},
])
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>Preview</SubHeader>
    <StationMap
        :stations="previewStations"
        :cluster="false"
        :fit-on-update="false"
        :initial-center="[51.0, 10.0]"
        :initial-zoom="6"
        height="280px"
    />
  </NeutralContainer>
</template>

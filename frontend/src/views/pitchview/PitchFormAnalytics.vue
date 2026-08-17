/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ChartsTab from '@/views/stationview/forms/analyticsview/ChartsTab.vue'
import MissingResponsesPanel from '@/views/stationview/forms/analyticsview/MissingResponsesPanel.vue'
import type {PitchFormAnalytics} from './pitchTypes'

/** The evaluation of a form, drawn by the application's own charts and pending-answers panel. */
defineProps<{
  analytics: PitchFormAnalytics
}>()
</script>

<template>
  <ChartsTab :questions="analytics.questions"/>
  <MissingResponsesPanel v-if="analytics.missing.length" :members="analytics.missing"/>
</template>

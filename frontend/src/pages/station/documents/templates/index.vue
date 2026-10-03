/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplatesView from '~/views/stationview/documents/TemplatesView.vue'
import {STATION_TEMPLATE_SCREENS} from '~/views/stationview/documents/templateScreens'

definePageMeta({
  layout: 'station',
  name: 'documents-templates',
})
</script>

<template>
  <TemplatesView :screens="STATION_TEMPLATE_SCREENS" />
</template>

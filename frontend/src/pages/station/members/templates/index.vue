/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplatesView from '~/views/stationview/members/TemplatesView.vue'
import {STATION_TEMPLATE_SCREENS} from '~/views/stationview/members/templateScreens'

definePageMeta({
  layout: 'station',
  name: 'member-document-templates',
})
</script>

<template>
  <TemplatesView :screens="STATION_TEMPLATE_SCREENS" />
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplatesView from '~/views/stationview/documents/TemplatesView.vue'
import {ASSOCIATION_TEMPLATE_SCREENS} from '~/views/stationview/documents/templateScreens'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-document-templates',
})
</script>

<template>
  <TemplatesView :screens="ASSOCIATION_TEMPLATE_SCREENS" />
</template>

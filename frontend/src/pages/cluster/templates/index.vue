/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplatesView from '~/views/stationview/members/TemplatesView.vue'
import {ASSOCIATION_TEMPLATE_SCREENS} from '~/views/stationview/members/templateScreens'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-document-templates',
})
</script>

<template>
  <TemplatesView :screens="ASSOCIATION_TEMPLATE_SCREENS" />
</template>

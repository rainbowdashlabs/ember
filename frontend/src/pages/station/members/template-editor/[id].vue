/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplateEditView from '~/views/stationview/members/TemplateEditView.vue'
import {STATION_TEMPLATE_SCREENS} from '~/views/stationview/members/templateScreens'

definePageMeta({
  layout: 'station',
  name: 'member-document-template-edit',
})
</script>

<template>
  <TemplateEditView :screens="STATION_TEMPLATE_SCREENS" />
</template>

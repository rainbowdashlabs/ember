/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TemplateEditView from '~/views/stationview/documents/TemplateEditView.vue'
import {ASSOCIATION_TEMPLATE_SCREENS} from '~/views/stationview/documents/templateScreens'
import {useClusterHomeStation} from '~/composables/useClusterHomeStation'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-document-template-edit',
})

const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <TemplateEditView v-if="homeStationId" :screens="ASSOCIATION_TEMPLATE_SCREENS" />
</template>

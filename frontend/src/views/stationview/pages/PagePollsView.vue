/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FormsListView from '@/views/stationview/forms/ListView.vue'
import { FormPurpose } from '@/api/types'
</script>

<template>
  <FormsListView
    :purpose="FormPurpose.POLL"
    :show-available-section="false"
    title-key="stationPages.editor.pollsTitle"
    analytics-route-name="pages-polls-analytics"
  />
</template>

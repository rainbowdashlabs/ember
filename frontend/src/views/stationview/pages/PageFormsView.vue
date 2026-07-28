/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FormsListView from '@/views/stationview/forms/ListView.vue'
import { FormPurpose } from '@/api/forms'
</script>

<template>
  <FormsListView
    :purpose="FormPurpose.CONTACT"
    :show-available-section="false"
    title-key="stationPages.editor.contactFormsTitle"
    analytics-route-name="pages-forms-submissions"
  />
</template>

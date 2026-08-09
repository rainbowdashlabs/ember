/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import CsvImportView from '~/views/stationview/quiz/CsvImportView.vue'

definePageMeta({
  layout: 'station',
  name: 'quiz-catalog-import',
})
</script>

<template>
  <CsvImportView />
</template>

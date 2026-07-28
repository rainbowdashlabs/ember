/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ReconsentView from '~/views/ReconsentView.vue'

definePageMeta({
  layout: 'default',
  name: 'reconsent',
})

useHead({
  title: 'Zustimmung',
})
</script>

<template>
  <ReconsentView />
</template>

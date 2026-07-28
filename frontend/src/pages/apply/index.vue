/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ApplyView from '~/views/ApplyView.vue'

definePageMeta({
  layout: 'default',
  name: 'apply',
})

useHead({
  title: 'Station beantragen',
})
</script>

<template>
  <ApplyView />
</template>

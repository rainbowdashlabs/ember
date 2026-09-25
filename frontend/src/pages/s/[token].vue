/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SharedPageView from '~/views/public/SharedPageView.vue'

definePageMeta({
  name: 'shared-page',
  public: true,
  layout: false,
})
</script>

<template>
  <SharedPageView />
</template>

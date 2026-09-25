/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ListView from '~/views/stationview/forms/ListView.vue'
import { FormPurpose } from '@/api/forms'

definePageMeta({
  layout: 'station',
  name: 'forms-list',
})
</script>

<template>
  <ListView :purpose="FormPurpose.INTERNAL" />
</template>

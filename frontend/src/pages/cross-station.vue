/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import CrossStationDashboardView from '~/views/CrossStationDashboardView.vue'

definePageMeta({
  layout: 'default',
  name: 'cross-station-dashboard',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.crossStation'),
})
</script>

<template>
  <CrossStationDashboardView />
</template>

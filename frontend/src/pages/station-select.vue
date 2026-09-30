/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import StationSelectView from '~/views/StationSelectView.vue'

definePageMeta({
  layout: 'default',
  name: 'station-select',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.stationSelect'),
})
</script>

<template>
  <StationSelectView />
</template>

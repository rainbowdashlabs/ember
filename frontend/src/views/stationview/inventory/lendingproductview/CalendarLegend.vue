/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SwatchLegend from '@/components/display/SwatchLegend.vue'

/** What the colours of the availability calendar stand for. */
const {t} = useI18n()

const entries = computed(() => [
  {label: t('lendingCalendar.allFree'), swatch: 'bg-success/25'},
  {label: t('lendingCalendar.someFree'), swatch: 'bg-info/30'},
  {label: t('lendingCalendar.noneFree'), swatch: 'bg-bg-light-accent dark:bg-bg-dark-accent'},
  {label: t('lendingCalendar.blocked'), swatch: '', hatched: true},
  {label: t('lendingCalendar.chosen'), swatch: 'ring-2 ring-primary'},
])
</script>

<template>
  <SwatchLegend :entries="entries"/>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SwatchLegend from '@/components/display/SwatchLegend.vue'

/** What the looks of the lending calendar's bars, blocks and marked days stand for. */
const {t} = useI18n()

const entries = computed(() => [
  {label: t('lendingCalendarPage.legend.requested'), swatch: 'border-2 border-primary'},
  {label: t('lendingCalendarPage.legend.approved'), swatch: 'bg-primary/60'},
  {label: t('lendingCalendarPage.legend.lent'), swatch: 'bg-primary'},
  {label: t('lendingCalendarPage.legend.returned'), swatch: 'bg-bg-light-accent dark:bg-bg-dark-accent'},
  {label: t('lendingCalendarPage.legend.overdue'), swatch: 'bg-error'},
  {label: t('lendingCalendarPage.legend.blocked'), swatch: '', hatched: true},
  {label: t('lendingCalendarPage.legend.clash'), swatch: 'bg-error/15'},
])
</script>

<template>
  <SwatchLegend :entries="entries" data-testid="lending-calendar-legend"/>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {GrowthDay, GrowthSeries} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import {categoryColor, seriesChart, useChartPaint} from './instanceCharts'

/**
 * How the instance grows, one small chart per figure. The figures differ by orders of magnitude,
 * so each keeps an axis of its own rather than sharing one. The history starts with the first daily
 * snapshot, which the line below the charts names.
 */
const props = defineProps<{
  growth: GrowthSeries
}>()

const {t} = useI18n()
const paint = useChartPaint()

const FIGURES = [
  'accounts',
  'members',
  'stations',
  'events',
  'attendanceEntries',
  'inventoryItems',
  'associations',
] as const satisfies ReadonlyArray<keyof GrowthDay>

const charts = computed(() => FIGURES.map((figure, index) => ({
  key: figure,
  option: seriesChart(
      'line',
      t(`adminStats.growthFigures.${figure}`),
      props.growth.days.map(d => d.day),
      [{
        name: t(`adminStats.growthFigures.${figure}`),
        data: props.growth.days.map(d => d[figure]),
        color: categoryColor(index),
      }],
      paint.value,
  ),
})))
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.growthOverTimeSection') }}</SubHeader>
    <div v-if="growth.days.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <NeutralContainer v-for="chart in charts" :key="chart.key">
        <VChart :option="chart.option" autoresize style="height: 200px"/>
      </NeutralContainer>
    </div>
    <MutedText v-if="growth.recordedSince" size="sm" tag="p">
      {{ t('adminStats.recordedSince', {date: formatDate(growth.recordedSince)}) }}
    </MutedText>
    <MutedText v-else size="sm" tag="p">{{ t('adminStats.notRecordedYet') }}</MutedText>
  </div>
</template>

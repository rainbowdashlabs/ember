/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart} from 'echarts/charts'
import {DataZoomComponent, GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import type {HourlyTotal} from '@/api/insights'
import {bottomLegend, cartesianGrid, ZOOM_SLIDER_BOTTOM} from '@/util/chartLayout'
import {darkThemeActive as isDark} from '@/util/themeState'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const props = defineProps<{
  rows: HourlyTotal[]
}>()

const {t, n} = useI18n()

const textColor = computed(() => (isDark.value ? '#ccc' : '#333'))

const labels = computed(() => props.rows.map(r => formatHourLabel(r.hour)))
const data = computed(() => props.rows.map(r => r.hits))

function formatHourLabel(iso: string): string {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleString(undefined, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const option = computed(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (v: unknown) => typeof v === 'number' ? n(v) : String(v),
  },
  legend: bottomLegend(textColor.value, [t('insights.chart.hits')]),
  grid: cartesianGrid({
    legend: true,
    axisName: true,
    rotatedLabels: true,
    zoom: props.rows.length > 24,
  }),
  xAxis: {
    type: 'category',
    data: labels.value,
    axisLabel: {color: textColor.value, rotate: 30, fontSize: 10},
  },
  yAxis: {
    type: 'value',
    name: t('insights.unitHits'),
    nameTextStyle: {color: textColor.value},
    axisLabel: {color: textColor.value},
  },
  series: [{
    name: t('insights.chart.hits'),
    type: 'bar' as const,
    data: data.value,
    itemStyle: {color: '#FF6421'},
  }],
  dataZoom: props.rows.length > 24 ? [{type: 'inside'}, {type: 'slider', bottom: ZOOM_SLIDER_BOTTOM}] : undefined,
}))
</script>

<template>
  <VChart v-if="rows.length > 0" :option="option" autoresize style="height: 320px"/>
</template>

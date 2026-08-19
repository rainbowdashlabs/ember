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
import {LineChart} from 'echarts/charts'
import {DataZoomComponent, GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import {AuthBucket, type AuthBucketName, type HourlyTrafficRow} from '@/api/traffic'
import {bottomLegend, cartesianGrid, ZOOM_SLIDER_BOTTOM} from '@/util/chartLayout'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

/**
 * Metric the chart visualises.
 *
 * <ul>
 *   <li>{@code ingressBytes} / {@code egressBytes} / {@code requests} - single-metric stacked
 *       area chart, one stack per auth bucket.</li>
 *   <li>{@code inout} - bidirectional view: ingress is drawn positive, egress is drawn
 *       negative, both stacked by auth bucket, so the chart shows a mirrored skyline around
 *       the zero axis.</li>
 * </ul>
 */
type Metric = 'ingressBytes' | 'egressBytes' | 'requests' | 'inout'

const props = defineProps<{
  rows: HourlyTrafficRow[]
  metric: Metric
}>()

const {t, n} = useI18n()

const isDark = computed(() => typeof document !== 'undefined' && document.documentElement.classList.contains('dark'))
const textColor = computed(() => (isDark.value ? '#ccc' : '#333'))

interface AuthSeries {
  bucket: AuthBucketName
  color: string
}

const seriesDefs: AuthSeries[] = [
  {bucket: AuthBucket.AUTHENTICATED, color: '#FF6421'},
  {bucket: AuthBucket.UNAUTHENTICATED, color: '#3694FF'},
  {bucket: AuthBucket.FEDERATION, color: '#00C507'},
]

const hours = computed(() => {
  const set = new Set<string>()
  for (const row of props.rows) set.add(row.hour)
  return [...set].sort()
})

const hourLabels = computed(() => hours.value.map(h => formatHourLabel(h)))

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

function bucketSum(hour: string, auth: AuthBucketName, field: 'ingressBytes' | 'egressBytes' | 'requests'): number {
  return props.rows
      .filter(r => r.hour === hour && r.auth === auth)
      .reduce((sum, r) => sum + (r[field] ?? 0), 0)
}

interface SeriesOption {
  name: string
  type: 'line'
  stack: string
  areaStyle: object
  smooth: boolean
  showSymbol: boolean
  data: number[]
  itemStyle: {color: string}
  lineStyle: {color: string}
}

function buildSeries(field: 'ingressBytes' | 'egressBytes' | 'requests', stackId: string, sign: 1 | -1, labelKey: 'in' | 'out' | null): SeriesOption[] {
  return seriesDefs.map(def => ({
    name: labelKey === null
        ? t(`traffic.bucket.${def.bucket}`)
        : t(`traffic.chart.directionBucket.${labelKey}`, {bucket: t(`traffic.bucket.${def.bucket}`)}),
    type: 'line' as const,
    stack: stackId,
    areaStyle: {opacity: 0.55},
    smooth: false,
    showSymbol: false,
    data: hours.value.map(h => bucketSum(h, def.bucket, field) * sign),
    itemStyle: {color: def.color},
    lineStyle: {color: def.color},
  }))
}

const series = computed<SeriesOption[]>(() => {
  if (props.metric === 'inout') {
    return [
      ...buildSeries('ingressBytes', 'in', 1, 'in'),
      ...buildSeries('egressBytes', 'out', -1, 'out'),
    ]
  }
  return buildSeries(props.metric, 'total', 1, null)
})

const yAxisName = computed(() => {
  if (props.metric === 'requests') return t('traffic.unitRequests')
  return t('traffic.unitBytes')
})

const valueFormatter = (value: number): string => {
  if (props.metric === 'requests') return n(value)
  return formatBytes(Math.abs(value))
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const value = bytes / Math.pow(1024, i)
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[i]}`
}

const option = computed(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (v: unknown) => typeof v === 'number' ? valueFormatter(v) : String(v),
  },
  legend: bottomLegend(textColor.value, series.value.map(s => s.name)),
  grid: cartesianGrid({
    legend: true,
    axisName: true,
    rotatedLabels: true,
    zoom: hours.value.length > 24,
    left: 70,
  }),
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: hourLabels.value,
    axisLabel: {color: textColor.value, rotate: 30, fontSize: 10},
  },
  yAxis: {
    type: 'value',
    name: yAxisName.value,
    nameTextStyle: {color: textColor.value},
    axisLabel: {
      color: textColor.value,
      formatter: (v: number) => valueFormatter(v),
    },
  },
  series: series.value,
  dataZoom: hours.value.length > 24 ? [{type: 'inside'}, {type: 'slider', bottom: ZOOM_SLIDER_BOTTOM}] : undefined,
}))
</script>

<template>
  <VChart v-if="rows.length > 0" :option="option" autoresize style="height: 320px"/>
</template>

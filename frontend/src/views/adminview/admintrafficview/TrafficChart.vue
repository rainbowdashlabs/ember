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
import {BarChart, LineChart} from 'echarts/charts'
import {DataZoomComponent, GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import {AuthBucket, type AuthBucketName, type HourlyTrafficRow} from '@/api/traffic'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const props = defineProps<{
  rows: HourlyTrafficRow[]
  /** Which byte metric the chart should visualise. */
  metric: 'ingressBytes' | 'egressBytes' | 'requests'
}>()

const {t, n} = useI18n()

const isDark = computed(() => typeof document !== 'undefined' && document.documentElement.classList.contains('dark'))
const textColor = computed(() => (isDark.value ? '#ccc' : '#333'))

interface AuthSeries {
  name: string
  bucket: AuthBucketName
  color: string
}

const seriesDefs: AuthSeries[] = [
  {name: 'AUTHENTICATED', bucket: AuthBucket.AUTHENTICATED, color: '#FF6421'},
  {name: 'UNAUTHENTICATED', bucket: AuthBucket.UNAUTHENTICATED, color: '#3694FF'},
  {name: 'FEDERATION', bucket: AuthBucket.FEDERATION, color: '#00C507'},
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

function bucketSum(hour: string, auth: AuthBucketName): number {
  return props.rows
      .filter(r => r.hour === hour && r.auth === auth)
      .reduce((sum, r) => sum + (r[props.metric] ?? 0), 0)
}

const series = computed(() => seriesDefs.map(def => ({
  name: t(`traffic.bucket.${def.bucket}`),
  type: 'bar' as const,
  stack: 'total',
  data: hours.value.map(h => bucketSum(h, def.bucket)),
  itemStyle: {color: def.color},
})))

const yAxisName = computed(() => {
  if (props.metric === 'requests') return t('traffic.unitRequests')
  return t('traffic.unitBytes')
})

const valueFormatter = (value: number): string => {
  if (props.metric === 'requests') return n(value)
  return formatBytes(value)
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
  legend: {
    data: seriesDefs.map(s => t(`traffic.bucket.${s.bucket}`)),
    textStyle: {color: textColor.value},
  },
  grid: {left: 70, right: 20, bottom: 60, top: 40},
  xAxis: {
    type: 'category',
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
  dataZoom: hours.value.length > 24 ? [{type: 'inside'}, {type: 'slider', bottom: 10}] : undefined,
}))
</script>

<template>
  <VChart v-if="rows.length > 0" :option="option" autoresize style="height: 320px"/>
</template>

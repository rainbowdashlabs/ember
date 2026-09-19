/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart} from 'echarts/charts'
import {GridComponent, TooltipComponent, LegendComponent, DataZoomComponent} from 'echarts/components'
import * as apiStatus from '@/api/apiStatus'
import type {EndpointStats, HourlyStats, StatusBreakdown} from '@/api/apiStatus'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {bottomLegend, cartesianGrid} from '@/util/chartLayout'
import {darkThemeActive as isDark} from '@/util/themeState'
import EndpointStatsPanel from './adminapistatusview/EndpointStatsPanel.vue'
import {formatMs} from './adminapistatusview/apiStatusFormat'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const {t} = useI18n()

const textColor = computed(() => isDark.value ? '#ccc' : '#333')

const slowest = ref<EndpointStats[]>([])
const fastest = ref<EndpointStats[]>([])
const failing = ref<EndpointStats[]>([])
const hourly = ref<HourlyStats[]>([])
const statusData = ref<StatusBreakdown[]>([])

const {loading, reload} = useAsyncLoader(async () => {
    const [s, fa, fl, h, st] = await Promise.all([
        apiStatus.getSlowest(),
        apiStatus.getFastest(),
        apiStatus.getFailing(),
        apiStatus.getHourlyStats(),
        apiStatus.getStatusBreakdown(),
    ])
    slowest.value = s
    fastest.value = fa
    failing.value = fl
    hourly.value = h
    statusData.value = st
})

const totalRequests = computed(() => hourly.value.reduce((sum, h) => sum + h.requestCount, 0))
const totalErrors = computed(() => hourly.value.reduce((sum, h) => sum + h.errorCount, 0))
const avgResponseTime = computed(() => {
    if (hourly.value.length === 0) return 0
    const total = hourly.value.reduce((sum, h) => sum + h.avgDurationMs * h.requestCount, 0)
    return totalRequests.value > 0 ? total / totalRequests.value : 0
})

const requestVolumeOption = computed(() => ({
    tooltip: {trigger: 'axis'},
    legend: bottomLegend(textColor.value, [t('apiStatus.requests'), t('apiStatus.errors')]),
    grid: cartesianGrid({legend: true, axisName: true, rotatedLabels: true}),
    xAxis: {type: 'category', data: hourly.value.map(h => h.hour.substring(11)), axisLabel: {color: textColor.value, rotate: 45}},
    yAxis: {type: 'value', name: t('apiStatus.count'), nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
    series: [
        {
            name: t('apiStatus.requests'),
            type: 'line',
            data: hourly.value.map(h => h.requestCount),
            itemStyle: {color: '#73CEFF'},
            areaStyle: {color: 'rgba(115,206,255,0.15)'},
            smooth: true,
        },
        {
            name: t('apiStatus.errors'),
            type: 'line',
            data: hourly.value.map(h => h.errorCount),
            itemStyle: {color: '#ec2929'},
            areaStyle: {color: 'rgba(236,41,41,0.1)'},
            smooth: true,
        },
    ],
}))

const responseTimeOption = computed(() => ({
    tooltip: {trigger: 'axis'},
    grid: cartesianGrid({axisName: true, rotatedLabels: true}),
    xAxis: {type: 'category', data: hourly.value.map(h => h.hour.substring(11)), axisLabel: {color: textColor.value, rotate: 45}},
    yAxis: {type: 'value', name: 'ms', nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
    series: [
        {
            name: t('apiStatus.avgResponseTime'),
            type: 'line',
            data: hourly.value.map(h => Math.round(h.avgDurationMs)),
            itemStyle: {color: '#FF6421'},
            smooth: true,
        },
    ],
}))

const statusChartOption = computed(() => {
    const grouped: Record<string, number> = {}
    for (const s of statusData.value) {
        const category = s.statusCode < 300 ? '2xx' : s.statusCode < 400 ? '3xx' : s.statusCode < 500 ? '4xx' : '5xx'
        grouped[category] = (grouped[category] ?? 0) + s.count
    }
    return {
        tooltip: {trigger: 'axis'},
        grid: cartesianGrid(),
        xAxis: {type: 'category', data: Object.keys(grouped), axisLabel: {color: textColor.value}},
        yAxis: {type: 'value', axisLabel: {color: textColor.value}},
        series: [{
            type: 'bar',
            data: Object.entries(grouped).map(([cat, count]) => ({
                value: count,
                itemStyle: {color: cat === '2xx' ? '#00C507' : cat === '3xx' ? '#73CEFF' : cat === '4xx' ? '#ffdd1b' : '#ec2929'},
            })),
        }],
    }
})

</script>

<template>
    <ViewContent :title="t('pages.admin-api-status.title')" :subtitle="t('pages.admin-api-status.subtitle')">
        <div class="flex items-center justify-between mb-4">
            <SecondaryButton :icon="['fas', 'rotate']" @click="reload">
                {{ t('common.refresh') }}
            </SecondaryButton>
        </div>

        <Spinner v-if="loading"/>

        <template v-else>
            <div class="grid grid-cols-3 gap-3 mb-6">
                <NeutralContainer class="text-center">
                    <p class="text-2xl font-bold">{{ totalRequests.toLocaleString('de-DE') }}</p>
                    <p class="text-xs text-[var(--text-muted)]">{{ t('apiStatus.totalRequests') }}</p>
                </NeutralContainer>
                <NeutralContainer class="text-center">
                    <p class="text-2xl font-bold">{{ formatMs(avgResponseTime) }}</p>
                    <p class="text-xs text-[var(--text-muted)]">{{ t('apiStatus.avgResponseTime') }}</p>
                </NeutralContainer>
                <component :is="totalErrors > 0 ? ErrorContainer : SuccessContainer" class="text-center">
                    <p class="text-2xl font-bold">{{ totalErrors }}</p>
                    <p class="text-xs text-[var(--text-muted)]">{{ t('apiStatus.serverErrors') }}</p>
                </component>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
                <NeutralContainer>
                    <SectionHeader>{{ t('apiStatus.requestsOverTime') }}</SectionHeader>
                    <VChart :option="requestVolumeOption" style="height: 280px" autoresize/>
                </NeutralContainer>
                <NeutralContainer>
                    <SectionHeader>{{ t('apiStatus.avgResponseTime') }}</SectionHeader>
                    <VChart :option="responseTimeOption" style="height: 280px" autoresize/>
                </NeutralContainer>
            </div>
            <div class="mb-6">
                <NeutralContainer>
                    <SectionHeader>{{ t('apiStatus.statusCodes') }}</SectionHeader>
                    <VChart :option="statusChartOption" style="height: 280px" autoresize/>
                </NeutralContainer>
            </div>

            <EndpointStatsPanel :fastest="fastest" :failing="failing" :slowest="slowest"/>
        </template>
    </ViewContent>
</template>

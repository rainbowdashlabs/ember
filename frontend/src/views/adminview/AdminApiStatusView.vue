/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart} from 'echarts/charts'
import {GridComponent, TooltipComponent, LegendComponent, DataZoomComponent} from 'echarts/components'
import * as apiStatus from '@/api/apiStatus'
import type {EndpointStats, HourlyStats, StatusBreakdown} from '@/api/apiStatus'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const {t} = useI18n()

const loading = ref(true)
const activeTab = ref<'overview' | 'slowest' | 'fastest' | 'failing'>('overview')

const slowest = ref<EndpointStats[]>([])
const fastest = ref<EndpointStats[]>([])
const failing = ref<EndpointStats[]>([])
const hourly = ref<HourlyStats[]>([])
const statusData = ref<StatusBreakdown[]>([])

async function loadData() {
    loading.value = true
    try {
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
    } catch {
        // ignore
    } finally {
        loading.value = false
    }
}

const totalRequests = computed(() => hourly.value.reduce((sum, h) => sum + h.requestCount, 0))
const totalErrors = computed(() => hourly.value.reduce((sum, h) => sum + h.errorCount, 0))
const avgResponseTime = computed(() => {
    if (hourly.value.length === 0) return 0
    const total = hourly.value.reduce((sum, h) => sum + h.avgDurationMs * h.requestCount, 0)
    return totalRequests.value > 0 ? total / totalRequests.value : 0
})

const requestVolumeOption = computed(() => ({
    tooltip: {trigger: 'axis'},
    legend: {data: [t('apiStatus.requests'), t('apiStatus.errors')]},
    grid: {left: 60, right: 20, bottom: 40},
    xAxis: {type: 'category', data: hourly.value.map(h => h.hour.substring(11))},
    yAxis: {type: 'value', name: t('apiStatus.count')},
    series: [
        {
            name: t('apiStatus.requests'),
            type: 'bar',
            data: hourly.value.map(h => h.requestCount),
            itemStyle: {color: '#73CEFF'},
        },
        {
            name: t('apiStatus.errors'),
            type: 'bar',
            data: hourly.value.map(h => h.errorCount),
            itemStyle: {color: '#ec2929'},
        },
    ],
}))

const responseTimeOption = computed(() => ({
    tooltip: {trigger: 'axis'},
    grid: {left: 60, right: 20, bottom: 40},
    xAxis: {type: 'category', data: hourly.value.map(h => h.hour.substring(11))},
    yAxis: {type: 'value', name: 'ms'},
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
        xAxis: {type: 'category', data: Object.keys(grouped)},
        yAxis: {type: 'value'},
        series: [{
            type: 'bar',
            data: Object.entries(grouped).map(([cat, count]) => ({
                value: count,
                itemStyle: {color: cat === '2xx' ? '#00C507' : cat === '3xx' ? '#73CEFF' : cat === '4xx' ? '#ffdd1b' : '#ec2929'},
            })),
        }],
    }
})

function formatMs(ms: number): string {
    return ms < 1 ? '<1ms' : Math.round(ms) + 'ms'
}

function formatPercent(rate: number): string {
    return (rate * 100).toFixed(1) + '%'
}

function methodColor(method: string): string {
    switch (method) {
        case 'GET': return 'text-green-600'
        case 'POST': return 'text-blue-600'
        case 'PUT': return 'text-yellow-600'
        case 'DELETE': return 'text-red-600'
        default: return 'text-[var(--text-muted)]'
    }
}

onMounted(loadData)
</script>

<template>
    <ViewContent>
        <div class="flex items-center justify-between mb-4">
            <PageHeader class="!mb-0">
                <font-awesome-icon :icon="['fas', 'chart-line']" class="mr-2"/>
                {{ t('apiStatus.title') }}
            </PageHeader>
            <SecondaryButton @click="loadData">
                <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1"/>
                {{ t('common.refresh') }}
            </SecondaryButton>
        </div>

        <Spinner v-if="loading"/>

        <template v-else>
            <!-- Summary cards -->
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

            <!-- Charts -->
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

            <!-- Tabs -->
            <div class="flex gap-2 mb-4">
                <SelectionToggleButton :selected="activeTab === 'slowest'" @toggle="activeTab = 'slowest'">
                    {{ t('apiStatus.slowest') }}
                </SelectionToggleButton>
                <SelectionToggleButton :selected="activeTab === 'fastest'" @toggle="activeTab = 'fastest'">
                    {{ t('apiStatus.fastest') }}
                </SelectionToggleButton>
                <SelectionToggleButton :selected="activeTab === 'failing'" @toggle="activeTab = 'failing'">
                    {{ t('apiStatus.failing') }}
                </SelectionToggleButton>
            </div>

            <!-- Endpoint table -->
            <NeutralContainer class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead>
                        <tr class="text-left text-xs text-[var(--text-muted)] border-b border-[var(--border)]">
                            <th class="py-2 pr-3">{{ t('apiStatus.endpoint') }}</th>
                            <th class="py-2 pr-3 text-right">{{ t('apiStatus.count') }}</th>
                            <th class="py-2 pr-3 text-right">{{ t('apiStatus.avg') }}</th>
                            <th class="py-2 pr-3 text-right">Min</th>
                            <th class="py-2 pr-3 text-right">Max</th>
                            <th class="py-2 text-right">{{ t('apiStatus.errorRate') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(ep, idx) in (activeTab === 'slowest' ? slowest : activeTab === 'fastest' ? fastest : failing)"
                            :key="idx"
                            class="border-b border-[var(--border)] last:border-0">
                            <td class="py-2 pr-3 font-mono text-xs">
                                <span :class="methodColor(ep.method)" class="font-semibold mr-1">{{ ep.method }}</span>
                                {{ ep.path }}
                            </td>
                            <td class="py-2 pr-3 text-right tabular-nums">{{ ep.requestCount }}</td>
                            <td class="py-2 pr-3 text-right tabular-nums">{{ formatMs(ep.avgDurationMs) }}</td>
                            <td class="py-2 pr-3 text-right tabular-nums text-[var(--text-muted)]">{{ formatMs(ep.minDurationMs) }}</td>
                            <td class="py-2 pr-3 text-right tabular-nums text-[var(--text-muted)]">{{ formatMs(ep.maxDurationMs) }}</td>
                            <td class="py-2 text-right tabular-nums" :class="ep.errorRate > 0.1 ? 'text-red-500 font-semibold' : ''">
                                {{ formatPercent(ep.errorRate) }}
                            </td>
                        </tr>
                    </tbody>
                </table>
            </NeutralContainer>
        </template>
    </ViewContent>
</template>

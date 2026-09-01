/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart} from 'echarts/charts'
import {GridComponent, TooltipComponent} from 'echarts/components'
import * as apiStatus from '@/api/apiStatus'
import type {EndpointDetail} from '@/api/apiStatus'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {darkThemeActive as isDark} from '@/util/themeState'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent])

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const textColor = computed(() => isDark.value ? '#ccc' : '#333')

const method = computed(() => route.query.method as string)
const path = computed(() => route.query.path as string)

const {config: detail, loading} = useConfigPanel<EndpointDetail | null>({
    initial: null,
    fetch: () => apiStatus.getEndpointDetail(method.value, path.value),
    formatError: () => '',
})

function methodColor(m: string): string {
    switch (m) {
        case 'GET': return 'text-green-600'
        case 'POST': return 'text-blue-600'
        case 'PUT': return 'text-yellow-600'
        case 'DELETE': return 'text-red-600'
        default: return 'text-(--text-muted)'
    }
}

function statusColor(code: number): string {
    if (code < 300) return 'text-success'
    if (code < 400) return 'text-secondary'
    if (code < 500) return 'text-[#ffdd1b]'
    return 'text-error'
}

function formatMs(ms: number): string {
    return ms < 1 ? '<1ms' : Math.round(ms) + 'ms'
}

function formatTimestamp(ts: string): string {
    return new Date(ts).toLocaleString('de-DE', {day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit'})
}

const statusChartOption = computed(() => {
    if (!detail.value) return {}
    const codes = detail.value.statusCodes
    return {
        tooltip: {trigger: 'axis'},
        grid: {left: 60, right: 20, bottom: 30},
        xAxis: {type: 'category', data: codes.map(c => String(c.statusCode)), axisLabel: {color: textColor.value}},
        yAxis: {type: 'value', name: t('apiStatus.count'), nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
        series: [{
            type: 'bar',
            data: codes.map(c => ({
                value: c.count,
                itemStyle: {
                    color: c.statusCode < 300 ? '#00C507'
                        : c.statusCode < 400 ? '#73CEFF'
                        : c.statusCode < 500 ? '#ffdd1b'
                        : '#ec2929',
                },
            })),
        }],
    }
})

const responseTimeChartOption = computed(() => {
    if (!detail.value) return {}
    const requests = [...detail.value.recentRequests].reverse()
    return {
        tooltip: {trigger: 'axis'},
        grid: {left: 60, right: 20, bottom: 30},
        xAxis: {type: 'category', data: requests.map(r => formatTimestamp(r.timestamp)), show: false},
        yAxis: {type: 'value', name: 'ms', nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
        series: [{
            type: 'line',
            data: requests.map(r => r.durationMs),
            itemStyle: {color: '#FF6421'},
            areaStyle: {color: 'rgba(255,100,33,0.1)'},
            smooth: true,
        }],
    }
})

</script>

<template>
    <ViewContent :title="t('pages.admin-api-status-detail.title')" :subtitle="t('pages.admin-api-status-detail.subtitle')">
        <div class="flex items-center gap-3 mb-6">
            <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.push({name: 'admin-api-status'})">
                {{ t('common.back') }}
            </SecondaryButton>
        </div>

        <Spinner v-if="loading"/>

        <template v-if="!loading && detail">
            <!-- Summary -->
            <div class="grid grid-cols-2 gap-3 mb-6">
                <NeutralContainer class="text-center">
                    <p class="text-2xl font-bold">{{ detail.requestCount.toLocaleString('de-DE') }}</p>
                    <p class="text-xs text-(--text-muted)">{{ t('apiStatus.totalRequests') }}</p>
                </NeutralContainer>
                <NeutralContainer class="text-center">
                    <p class="text-2xl font-bold">{{ formatMs(detail.avgDurationMs) }}</p>
                    <p class="text-xs text-(--text-muted)">{{ t('apiStatus.avgResponseTime') }}</p>
                </NeutralContainer>
            </div>

            <!-- Charts -->
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
                <NeutralContainer>
                    <SectionHeader>{{ t('apiStatus.statusCodes') }}</SectionHeader>
                    <VChart :option="statusChartOption" style="height: 250px" autoresize/>
                </NeutralContainer>
                <NeutralContainer>
                    <SectionHeader>{{ t('apiStatus.responseTimeLine') }}</SectionHeader>
                    <VChart :option="responseTimeChartOption" style="height: 250px" autoresize/>
                </NeutralContainer>
            </div>

            <!-- Recent requests -->
            <NeutralContainer>
                <SectionHeader>{{ t('apiStatus.recentRequests') }}</SectionHeader>
                <div class="overflow-x-auto mt-3">
                    <table class="w-full text-sm">
                        <THead>
                            <Th>{{ t('apiStatus.timestamp') }}</Th>
                            <Th align="right">{{ t('apiStatus.statusCode') }}</Th>
                            <Th align="right">{{ t('apiStatus.duration') }}</Th>
                        </THead>
                        <tbody>
                            <TRow v-for="(req, idx) in detail.recentRequests" :key="idx">
                                <Td class="font-mono text-xs">{{ formatTimestamp(req.timestamp) }}</Td>
                                <Td align="right" :class="statusColor(req.statusCode)" class="font-semibold tabular-nums">{{ req.statusCode }}</Td>
                                <Td align="right" class="tabular-nums">{{ formatMs(req.durationMs) }}</Td>
                            </TRow>
                        </tbody>
                    </table>
                </div>
            </NeutralContainer>
        </template>
    </ViewContent>
</template>

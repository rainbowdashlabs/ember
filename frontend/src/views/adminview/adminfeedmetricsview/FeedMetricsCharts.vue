/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import type {EChartsCoreOption} from 'echarts/core'
import ChartPanel from '@/components/chart/ChartPanel.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  hasData: boolean
  requestsChartOption: EChartsCoreOption
  histogramChartOption: EChartsCoreOption
  volumeChartOption: EChartsCoreOption
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <ChartPanel :empty-text="t('feedMetrics.noData')" :has-data="hasData" :option="requestsChartOption"
                :title="t('feedMetrics.requestsByType')"/>

    <ChartPanel :empty-text="t('feedMetrics.noData')" :has-data="hasData" :option="histogramChartOption"
                :title="t('feedMetrics.latencyHistogram')">
      <MutedText size="sm" tag="p">{{ t('feedMetrics.latencyHistogramHint') }}</MutedText>
    </ChartPanel>

    <ChartPanel :empty-text="t('feedMetrics.noData')" :has-data="hasData" :height="240"
                :option="volumeChartOption" :title="t('feedMetrics.dailyVolume')"/>
  </div>
</template>

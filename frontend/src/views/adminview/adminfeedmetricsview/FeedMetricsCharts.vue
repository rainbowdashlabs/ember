/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import type {EChartsCoreOption} from 'echarts/core'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
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
  <div class="contents">
    <NeutralContainer class="space-y-2">
      <SectionHeader>{{ t('feedMetrics.requestsByType') }}</SectionHeader>
      <VChart v-if="hasData" :option="requestsChartOption" autoresize style="height: 280px"/>
      <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
    </NeutralContainer>

    <NeutralContainer class="space-y-2">
      <SectionHeader>{{ t('feedMetrics.latencyHistogram') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('feedMetrics.latencyHistogramHint') }}</MutedText>
      <VChart v-if="hasData" :option="histogramChartOption" autoresize style="height: 280px"/>
      <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
    </NeutralContainer>

    <NeutralContainer class="space-y-2">
      <SectionHeader>{{ t('feedMetrics.dailyVolume') }}</SectionHeader>
      <VChart v-if="hasData" :option="volumeChartOption" autoresize style="height: 240px"/>
      <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
    </NeutralContainer>
  </div>
</template>

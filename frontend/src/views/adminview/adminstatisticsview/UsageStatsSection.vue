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
import StatTile from '@/components/statistic/StatTile.vue'
import type {UsageSeries} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import {CATEGORY_COLORS, seriesChart, useChartPaint} from './instanceCharts'

/**
 * How many people use the instance: today so far, the average day of the last thirty, and the
 * series over the chosen window. A longer window draws one point per week, the average of its days.
 */
const props = defineProps<{
  usage: UsageSeries
  byWeek: boolean
}>()

const {t, locale} = useI18n()
const paint = useChartPaint()

const option = computed(() => seriesChart(
    'line',
    t(props.byWeek ? 'adminStats.activePerWeek' : 'adminStats.activePerDay'),
    props.usage.days.map(d => d.day),
    [{name: t('adminStats.activePerDay'), data: props.usage.days.map(d => d.count), color: CATEGORY_COLORS[0]}],
    paint.value,
))
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.usageSection') }}</SubHeader>
    <div class="grid grid-cols-2 gap-4">
      <StatTile :label="t('adminStats.activeToday')" :value="usage.today"/>
      <StatTile :label="t('adminStats.activeAverage')" :value="usage.average30.toLocaleString(locale)"/>
    </div>
    <NeutralContainer v-if="usage.days.length > 0">
      <VChart :option="option" autoresize style="height: 260px"/>
    </NeutralContainer>
    <MutedText v-if="usage.recordedSince" size="sm" tag="p">
      {{ t('adminStats.recordedSince', {date: formatDate(usage.recordedSince)}) }}
    </MutedText>
    <MutedText v-else size="sm" tag="p">{{ t('adminStats.notRecordedYet') }}</MutedText>
  </div>
</template>

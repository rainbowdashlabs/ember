/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TrafficChart from './TrafficChart.vue'
import type {HourlyTrafficRow} from '@/api/traffic'

defineProps<{
  rows: HourlyTrafficRow[]
  metric: 'ingressBytes' | 'egressBytes' | 'requests' | 'inout'
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SectionHeader>{{ t('traffic.chart.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('traffic.chart.hint') }}</MutedText>
    <TrafficChart v-if="rows.length > 0" :rows="rows" :metric="metric"/>
    <MutedText v-else tag="div" size="sm">{{ t('traffic.noData') }}</MutedText>
  </NeutralContainer>
</template>

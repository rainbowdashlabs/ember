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
import StatTile from '@/components/statistic/StatTile.vue'
import {FederationStatus, type FederationFigures} from '@/api/generated/schema'
import {CATEGORY_COLORS, donutChart, seriesChart, useChartPaint} from './instanceCharts'

/**
 * Federation across the instance: who works with whom, what they share, what they lend in the
 * window, which other instances are known, and the associations. The section carries the anchor
 * `#federation`, which the overview links to for pending partnerships.
 */
const props = defineProps<{
  federation: FederationFigures
}>()

const {t} = useI18n()
const paint = useChartPaint()

const FIGURES = [
  'localPartners',
  'remotePartners',
  'stationsWithPartner',
  'partnerInstances',
  'peersKnown',
  'peersReachable',
  'associations',
  'stationsInAssociations',
] as const satisfies ReadonlyArray<keyof FederationFigures>

const STATUS_COLORS: Record<FederationStatus, string> = {
  [FederationStatus.ACTIVE]: '#00C507',
  [FederationStatus.PENDING]: '#ffdd1b',
  [FederationStatus.SUSPENDED]: '#CFCFCF',
}

const tiles = computed(() => FIGURES.map(figure => ({
  key: figure,
  label: t(`adminStats.federationFigures.${figure}`),
  value: props.federation[figure],
})))

const partnershipsOption = computed(() => donutChart(
    t('adminStats.partnerships'),
    props.federation.partnersByStatus.map(s => ({
      name: t(`adminStats.partnerStatus.${s.status}`),
      value: s.count,
      color: STATUS_COLORS[s.status],
    })),
    paint.value,
))

const anyShares = computed(() => props.federation.sharesByKind.some(s => s.count > 0))

const sharesOption = computed(() => seriesChart(
    'bar',
    t('adminStats.sharesByKind'),
    props.federation.sharesByKind.map(s => t(`adminStats.shareKinds.${s.kind}`)),
    [{name: t('adminStats.sharesByKind'), data: props.federation.sharesByKind.map(s => s.count), color: CATEGORY_COLORS[4]}],
    paint.value,
))

const lendingOption = computed(() => seriesChart(
    'bar',
    t('adminStats.lendingPerMonth'),
    props.federation.lendingByMonth.map(m => m.month),
    [{name: t('adminStats.lendingPerMonth'), data: props.federation.lendingByMonth.map(m => m.count), color: CATEGORY_COLORS[0]}],
    paint.value,
))
</script>

<template>
  <div id="federation" class="space-y-4 scroll-mt-4">
    <SubHeader>{{ t('adminStats.federationSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <StatTile v-for="tile in tiles" :key="tile.key" :label="tile.label" :value="tile.value"/>
    </div>
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
      <NeutralContainer v-if="federation.partnersByStatus.length > 0">
        <VChart :option="partnershipsOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="anyShares">
        <VChart :option="sharesOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="federation.lendingByMonth.length > 0">
        <VChart :option="lendingOption" autoresize style="height: 260px"/>
      </NeutralContainer>
    </div>
  </div>
</template>

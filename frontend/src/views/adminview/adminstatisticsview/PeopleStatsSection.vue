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
import type {PeopleByType, PeopleFigures} from '@/api/generated/schema'
import {categoryColor, seriesChart, useChartPaint} from './instanceCharts'

/**
 * The people of the instance by member type: one figure per type, the people in all and the
 * memberships, and the types over time from the daily snapshot. One person can hold several types
 * in different stations, which the hint says, because the types then add up to more than the total.
 */
const props = defineProps<{
  people: PeopleFigures
}>()

const {t} = useI18n()
const paint = useChartPaint()

const TYPES = [
  {key: 'MEMBER', field: 'member'},
  {key: 'GUARDIAN', field: 'guardian'},
  {key: 'TRIAL', field: 'trial'},
  {key: 'TEAM', field: 'team'},
  {key: 'MANAGER', field: 'manager'},
] as const satisfies ReadonlyArray<{key: string; field: keyof PeopleByType}>

const typeTiles = computed(() => TYPES.map(type => ({
  key: type.key,
  label: t(`adminStats.peopleTypes.${type.key}`),
  value: props.people.current[type.field],
})))

const option = computed(() => seriesChart(
    'line',
    t('adminStats.peopleOverTime'),
    props.people.byDay.map(d => d.day),
    TYPES.map((type, index) => ({
      name: t(`adminStats.peopleTypes.${type.key}`),
      data: props.people.byDay.map(d => d.counts[type.field]),
      color: categoryColor(index),
    })),
    paint.value,
    true,
))
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.peopleSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-4">
      <StatTile v-for="tile in typeTiles" :key="tile.key" :label="tile.label" :value="tile.value"/>
      <StatTile :label="t('adminStats.peopleTotal')" :value="people.current.people" color="success"/>
      <StatTile :label="t('adminStats.memberships')" :value="people.current.memberships" color="muted"/>
    </div>
    <MutedText size="sm" tag="p">{{ t('adminStats.peopleHint') }}</MutedText>
    <NeutralContainer v-if="people.byDay.length > 0">
      <VChart :option="option" autoresize style="height: 280px"/>
    </NeutralContainer>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatTile from '@/components/statistic/StatTile.vue'
import MutedText from '@/components/typography/MutedText.vue'
import WindowField from '@/views/adminview/admintrafficview/WindowField.vue'
import {StatisticsWindow} from '@/api/generated/schema'

const {t} = useI18n()

const emailTiles = computed(() => [
  {key: 'sentToday', value: 12, color: 'success' as const},
  {key: 'queued', value: 3, color: 'info' as const},
  {key: 'sending', value: 1, color: 'info' as const},
  {key: 'totalSent', value: 847},
  {key: 'failed', value: 0, color: 'muted' as const},
  {key: 'providerBlocks', value: 0, color: 'muted' as const},
])

const platformTiles = computed(() => [
  {key: 'stations', value: 3},
  {key: 'accounts', value: 45},
  {key: 'members', value: 82},
  {key: 'activeSessions', value: 7},
  {key: 'groups', value: 12},
  {key: 'twoFactorAccounts', value: 28},
])

const dataTiles = computed(() => [
  {key: 'events', value: 18},
  {key: 'eventsUpcoming', value: 5},
  {key: 'eventRegistrations', value: 132},
  {key: 'attendanceSessions', value: 64},
  {key: 'sessionsThisMonth', value: 6},
  {key: 'attendanceEntries', value: 1280},
  {key: 'inventoryItems', value: 96},
  {key: 'profileFields', value: 24},
])

const chartTiles = computed(() => [
  {key: 'emailHistory', icon: 'chart-bar'},
  {key: 'emailStatus', icon: 'chart-pie'},
  {key: 'registrationsGrowth', icon: 'chart-line'},
])

const windowOptions = computed(() => Object.values(StatisticsWindow).map(w => ({
  value: w,
  label: t(`adminStats.statisticsWindow.${w}`),
})))

const usageTiles = computed(() => [
  {key: 'activeToday', value: 14},
  {key: 'activeAverage', value: '11,3'},
])

const peopleTiles = computed(() => [
  {label: t('adminStats.peopleTypes.MEMBER'), value: 50},
  {label: t('adminStats.peopleTypes.GUARDIAN'), value: 10},
  {label: t('adminStats.peopleTypes.TRIAL'), value: 5},
  {label: t('adminStats.peopleTypes.TEAM'), value: 18},
  {label: t('adminStats.peopleTypes.MANAGER'), value: 3},
])

const seriesCharts = computed(() => [
  {key: 'activePerDay', icon: 'chart-line'},
  {key: 'peopleOverTime', icon: 'chart-line'},
  {key: 'growthFigures.accounts', icon: 'chart-line'},
  {key: 'attendancePerMonth', icon: 'chart-bar'},
  {key: 'partnerships', icon: 'chart-pie'},
  {key: 'sharesByKind', icon: 'chart-bar'},
])
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStatistics.title')" :subtitle="t('helpCenter.adminStatistics.subtitle')">
    <HelpSection :title="t('helpCenter.adminStatistics.whatShown')">
      <p>{{ t('helpCenter.adminStatistics.whatShownText') }}</p>
      <p>{{ t('helpCenter.adminStatistics.emailSystem') }}</p>
      <p>{{ t('helpCenter.adminStatistics.platform') }}</p>
      <p>{{ t('helpCenter.adminStatistics.dataStore') }}</p>
      <p>{{ t('helpCenter.adminStatistics.charts') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStatistics.overviewTitle')">
      <SubHeader>{{ t('adminStats.emailSection') }}</SubHeader>
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatTile v-for="tile in emailTiles" :key="tile.key" :color="tile.color"
                  :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <NeutralContainer v-for="chart in chartTiles" :key="chart.key" class="flex items-center justify-center"
                          style="height: 200px">
          <div class="text-center text-(--text-muted)">
            <font-awesome-icon :icon="['fas', chart.icon]" class="text-3xl mb-2"/>
            <p class="text-sm">{{ t(`adminStats.${chart.key}`) }}</p>
          </div>
        </NeutralContainer>
      </div>

      <SubHeader>{{ t('adminStats.platformSection') }}</SubHeader>
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatTile v-for="tile in platformTiles" :key="tile.key" :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
      </div>

      <SubHeader>{{ t('adminStats.dataSection') }}</SubHeader>
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatTile v-for="tile in dataTiles" :key="tile.key" :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStatistics.windowTitle')">
      <p>{{ t('helpCenter.adminStatistics.windowText') }}</p>
      <NeutralContainer>
        <WindowField :model-value="StatisticsWindow.DAYS_30" :options="windowOptions"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStatistics.usageTitle')">
      <p>{{ t('helpCenter.adminStatistics.usageText') }}</p>
      <p>{{ t('helpCenter.adminStatistics.usageCounting') }}</p>
      <p>{{ t('helpCenter.adminStatistics.emptyText') }}</p>
      <div class="grid grid-cols-2 gap-4">
        <StatTile v-for="tile in usageTiles" :key="tile.key" :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStatistics.peopleTitle')">
      <p>{{ t('helpCenter.adminStatistics.peopleText') }}</p>
      <p>{{ t('helpCenter.adminStatistics.peopleTypesText') }}</p>
      <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-4">
        <StatTile v-for="tile in peopleTiles" :key="tile.label" :label="tile.label" :value="tile.value"/>
        <StatTile :label="t('adminStats.peopleTotal')" :value="72" color="success"/>
        <StatTile :label="t('adminStats.memberships')" :value="81" color="muted"/>
      </div>
      <MutedText size="sm" tag="p">{{ t('adminStats.peopleHint') }}</MutedText>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStatistics.growthTitle')">
      <p>{{ t('helpCenter.adminStatistics.growthText') }}</p>
      <p>{{ t('helpCenter.adminStatistics.attendanceText') }}</p>
      <p>{{ t('helpCenter.adminStatistics.federationText') }}</p>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <NeutralContainer v-for="chart in seriesCharts" :key="chart.key" class="flex items-center justify-center"
                          style="height: 160px">
          <div class="text-center text-(--text-muted)">
            <font-awesome-icon :icon="['fas', chart.icon]" class="text-3xl mb-2"/>
            <p class="text-sm">{{ t(`adminStats.${chart.key}`) }}</p>
          </div>
        </NeutralContainer>
      </div>
      <MutedText size="sm" tag="p">{{ t('adminStats.recordedSince', {date: '11.11.2026'}) }}</MutedText>
      <MutedText size="sm" tag="p">{{ t('adminStats.notRecordedYet') }}</MutedText>
      <MutedText size="sm" tag="p">{{ t('adminStats.noAttendance') }}</MutedText>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminStatistics.tip') }}</HelpTip>
  </HelpArticle>
</template>

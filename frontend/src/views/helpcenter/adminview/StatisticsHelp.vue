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
  {key: 'attendanceStatus', icon: 'chart-pie'},
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

    <HelpTip>{{ t('helpCenter.adminStatistics.tip') }}</HelpTip>
  </HelpArticle>
</template>

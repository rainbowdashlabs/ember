/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatValue from '@/components/typography/StatValue.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStatistics.title')" :subtitle="t('helpCenter.adminStatistics.subtitle')">
    <HelpSection :title="t('helpCenter.adminStatistics.whatShown')">
      <p>{{ t('helpCenter.adminStatistics.whatShownText') }}</p>
      <p>{{ t('helpCenter.adminStatistics.emailSystem') }}</p>
      <p>{{ t('helpCenter.adminStatistics.platform') }}</p>
      <p>{{ t('helpCenter.adminStatistics.dataStore') }}</p>
    </HelpSection>

    <!-- Dummy: Email stats -->
    <SubHeader>{{ t('adminStats.emailSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <NeutralContainer class="text-center">
        <StatValue color="success">12</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.sentToday') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold text-info">3</p>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.queued') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold">847</p>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.totalSent') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold">0</p>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.failed') }}</p>
      </NeutralContainer>
    </div>

    <!-- Dummy: Chart placeholders -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <NeutralContainer class="flex items-center justify-center" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('adminStats.emailHistory') }}</p>
        </div>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-center" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-pie']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('adminStats.emailStatus') }}</p>
        </div>
      </NeutralContainer>
    </div>

    <!-- Dummy: Platform stats -->
    <SubHeader>{{ t('adminStats.platformSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-4">
      <NeutralContainer class="text-center">
        <StatValue>3</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.stations') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>45</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.accounts') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>82</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.members') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>7</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.activeSessions') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>12</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.groups') }}</p>
      </NeutralContainer>
    </div>

    <!-- Dummy: Data stats -->
    <SubHeader>{{ t('adminStats.dataSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-4">
      <NeutralContainer class="text-center">
        <StatValue>18</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.events') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>64</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.attendanceSessions') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>1280</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.attendanceEntries') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>96</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.inventoryItems') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>24</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.profileFields') }}</p>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.adminStatistics.tip') }}</HelpTip>
  </HelpArticle>
</template>

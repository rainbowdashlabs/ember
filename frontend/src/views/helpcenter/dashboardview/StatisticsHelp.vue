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

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.statistics.title')" :subtitle="t('helpCenter.statistics.subtitle')">
    <HelpSection :title="t('helpCenter.statistics.whatIs')">
      <p>{{ t('helpCenter.statistics.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.statistics.whatShown')">
      <p>{{ t('helpCenter.statistics.membersGroups') }}</p>
      <p>{{ t('helpCenter.statistics.attendanceMonth') }}</p>
      <p>{{ t('helpCenter.statistics.sessionsMonth') }}</p>
      <p>{{ t('helpCenter.statistics.inventoryStatus') }}</p>
      <p>{{ t('helpCenter.statistics.eventRegistrations') }}</p>
    </HelpSection>

    <!-- Dummy: Summary cards -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold text-primary">42</p>
        <p class="text-sm text-(--text-muted)">{{ t('statistics.members') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold text-primary">4</p>
        <p class="text-sm text-(--text-muted)">{{ t('statistics.groups') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold text-primary">28</p>
        <p class="text-sm text-(--text-muted)">{{ t('statistics.totalSessions') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold text-primary">96</p>
        <p class="text-sm text-(--text-muted)">{{ t('statistics.totalItems') }}</p>
      </NeutralContainer>
    </div>

    <!-- Dummy: Chart placeholders -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <NeutralContainer class="flex items-center justify-center" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-pie']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('statistics.groupDistribution') }}</p>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex items-center justify-center" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-line']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('statistics.sessionsPerMonth') }}</p>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex items-center justify-center lg:col-span-2" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('statistics.attendanceOverTime') }}</p>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex items-center justify-center" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('statistics.inventoryStatus') }}</p>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex items-center justify-center" style="height: 200px">
        <div class="text-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="text-3xl mb-2"/>
          <p class="text-sm">{{ t('statistics.eventRegistrations') }}</p>
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.statistics.tip') }}</HelpTip>
  </HelpArticle>
</template>

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
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  sessionsByDayCount: number
  registrationsByDayCount: number
  topStationsCount: number
  sessionsByDayOption: EChartsCoreOption
  registrationsByDayOption: EChartsCoreOption
  topStationsOption: EChartsCoreOption
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.growthSection') }}</SubHeader>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <NeutralContainer v-if="sessionsByDayCount > 0">
        <VChart :option="sessionsByDayOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="registrationsByDayCount > 0">
        <VChart :option="registrationsByDayOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="topStationsCount > 0" class="lg:col-span-2">
        <VChart :option="topStationsOption" autoresize style="height: 260px"/>
      </NeutralContainer>
    </div>
  </div>
</template>

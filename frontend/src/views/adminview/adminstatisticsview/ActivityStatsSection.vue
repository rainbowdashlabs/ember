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
  signUpsByDayCount: number
  topStationsCount: number
  signUpsByDayOption: EChartsCoreOption
  topStationsOption: EChartsCoreOption
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.growthSection') }}</SubHeader>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <NeutralContainer v-if="signUpsByDayCount > 0">
        <VChart :option="signUpsByDayOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="topStationsCount > 0">
        <VChart :option="topStationsOption" autoresize style="height: 260px"/>
      </NeutralContainer>
    </div>
  </div>
</template>

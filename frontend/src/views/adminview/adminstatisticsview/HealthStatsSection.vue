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
  verifiedCount: number
  setupCount: number
  attendanceStatusCount: number
  verifiedOption: EChartsCoreOption
  setupOption: EChartsCoreOption
  attendanceStatusOption: EChartsCoreOption
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.healthSection') }}</SubHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <NeutralContainer v-if="verifiedCount > 0">
        <VChart :option="verifiedOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="setupCount > 0">
        <VChart :option="setupOption" autoresize style="height: 260px"/>
      </NeutralContainer>
      <NeutralContainer v-if="attendanceStatusCount > 0">
        <VChart :option="attendanceStatusOption" autoresize style="height: 260px"/>
      </NeutralContainer>
    </div>
  </div>
</template>

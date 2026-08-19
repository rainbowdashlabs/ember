/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import type {EChartsCoreOption} from 'echarts/core'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatTile from '@/components/statistic/StatTile.vue'

const props = defineProps<{
  emailSentToday: number
  emailPending: number
  emailSending: number
  emailSent: number
  emailFailed: number
  mailProviderBlocks: number
  emailByDayCount: number
  emailByStatusCount: number
  emailByDayOption: EChartsCoreOption
  emailStatusOption: EChartsCoreOption
}>()

const {t} = useI18n()

const tiles = computed(() => [
  {key: 'sentToday', value: props.emailSentToday, color: 'success' as const},
  {key: 'queued', value: props.emailPending, highlight: 'info' as const},
  {key: 'sending', value: props.emailSending, highlight: 'info' as const},
  {key: 'totalSent', value: props.emailSent},
  {key: 'failed', value: props.emailFailed, highlight: 'error' as const},
  {key: 'providerBlocks', value: props.mailProviderBlocks, highlight: 'error' as const},
])
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.emailSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
      <StatTile v-for="tile in tiles" :key="tile.key" :color="tile.color" :highlight="tile.highlight"
                :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
    </div>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <NeutralContainer v-if="emailByDayCount > 0">
        <VChart :option="emailByDayOption" autoresize style="height: 300px"/>
      </NeutralContainer>
      <NeutralContainer v-if="emailByStatusCount > 0">
        <VChart :option="emailStatusOption" autoresize style="height: 300px"/>
      </NeutralContainer>
    </div>
  </div>
</template>

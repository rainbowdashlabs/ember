/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatValue from '@/components/typography/StatValue.vue'

defineProps<{
  emailSentToday: number
  emailPending: number
  emailSent: number
  emailFailed: number
  emailByDayCount: number
  emailByStatusCount: number
  emailByDayOption: object
  emailStatusOption: object
}>()

const {t} = useI18n()
</script>

<template>
  <div class="contents">
    <SubHeader>{{ t('adminStats.emailSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <NeutralContainer class="text-center">
        <StatValue color="success">{{ emailSentToday }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.sentToday') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p :class="emailPending > 0 ? 'text-info' : ''" class="text-2xl font-bold">{{ emailPending }}</p>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.queued') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p class="text-2xl font-bold">{{ emailSent }}</p>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.totalSent') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <p :class="emailFailed > 0 ? 'text-error' : ''" class="text-2xl font-bold">{{ emailFailed }}</p>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.failed') }}</p>
      </NeutralContainer>
    </div>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <NeutralContainer v-if="emailByDayCount > 0">
        <VChart :option="emailByDayOption" autoresize style="height: 300px"/>
      </NeutralContainer>
      <NeutralContainer v-if="emailByStatusCount > 0">
        <VChart :option="emailStatusOption" autoresize style="height: 300px"/>
      </NeutralContainer>
    </div>
  </div>
</template>

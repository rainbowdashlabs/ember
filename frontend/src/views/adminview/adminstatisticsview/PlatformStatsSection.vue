/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatValue from '@/components/typography/StatValue.vue'

defineProps<{
  totalStations: number
  totalAccounts: number
  totalMembers: number
  activeSessions: number
  totalGroups: number
}>()

const {t} = useI18n()
</script>

<template>
  <div class="contents">
    <SubHeader>{{ t('adminStats.platformSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-4">
      <NeutralContainer class="text-center">
        <StatValue>{{ totalStations }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.stations') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalAccounts }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.accounts') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalMembers }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.members') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ activeSessions }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.activeSessions') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalGroups }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.groups') }}</p>
      </NeutralContainer>
    </div>
  </div>
</template>

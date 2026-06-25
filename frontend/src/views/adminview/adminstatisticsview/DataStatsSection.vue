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
  totalEvents: number
  totalAttendanceSessions: number
  totalAttendanceEntries: number
  totalInventoryItems: number
  totalProfileFields: number
}>()

const {t} = useI18n()
</script>

<template>
  <div class="contents">
    <SubHeader>{{ t('adminStats.dataSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-4">
      <NeutralContainer class="text-center">
        <StatValue>{{ totalEvents }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.events') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalAttendanceSessions }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.attendanceSessions') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalAttendanceEntries }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.attendanceEntries') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalInventoryItems }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.inventoryItems') }}</p>
      </NeutralContainer>
      <NeutralContainer class="text-center">
        <StatValue>{{ totalProfileFields }}</StatValue>
        <p class="text-sm text-(--text-muted)">{{ t('adminStats.profileFields') }}</p>
      </NeutralContainer>
    </div>
  </div>
</template>

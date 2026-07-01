/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatusBadge from './StatusBadge.vue'
import type {DataTrackingSummary} from '@/api/dataTracking'

defineProps<{
  summary: DataTrackingSummary
}>()

const {t} = useI18n()
</script>

<template>
  <div class="contents">
    <SubHeader>{{ t('adminDataTracking.statusBreakdown') }}</SubHeader>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-6">
      <NeutralContainer>
        <div class="font-semibold mb-2">{{ t('adminDataTracking.stationTransfer') }}</div>
        <div class="flex items-center gap-2 text-sm flex-wrap">
          <StatusBadge status="TRACKED" :count="summary.stationTransfer.tracked"/>
          <StatusBadge status="IGNORED" :count="summary.stationTransfer.ignored"/>
          <StatusBadge status="UNVERIFIED" :count="summary.stationTransfer.unverified"/>
        </div>
      </NeutralContainer>
      <NeutralContainer>
        <div class="font-semibold mb-2">{{ t('adminDataTracking.gdprExport') }}</div>
        <div class="flex items-center gap-2 text-sm flex-wrap">
          <StatusBadge status="TRACKED" :count="summary.gdprExport.tracked"/>
          <StatusBadge status="IGNORED" :count="summary.gdprExport.ignored"/>
          <StatusBadge status="UNVERIFIED" :count="summary.gdprExport.unverified"/>
        </div>
      </NeutralContainer>
      <NeutralContainer>
        <div class="font-semibold mb-2">{{ t('adminDataTracking.gdprDeletion') }}</div>
        <div class="flex items-center gap-2 text-sm flex-wrap">
          <StatusBadge status="TRACKED" :count="summary.gdprDeletion.tracked"/>
          <StatusBadge status="IGNORED" :count="summary.gdprDeletion.ignored"/>
          <StatusBadge status="UNVERIFIED" :count="summary.gdprDeletion.unverified"/>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>

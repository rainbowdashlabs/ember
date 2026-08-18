/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import type {DataTrackingSummary} from '@/api/dataTracking'

defineProps<{
  summary: DataTrackingSummary
  needsReviewCount: number
  verifiedPct: number
  schemaHash: string | undefined
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
    <NeutralContainer class="!py-3">
      <div class="text-xs text-(--text-muted) uppercase">{{ t('adminDataTracking.totalTables') }}</div>
      <div class="text-2xl font-bold">{{ summary.totalTables }}</div>
    </NeutralContainer>
    <SuccessContainer class="!py-3">
      <div class="text-xs uppercase">{{ t('adminDataTracking.columnsVerified') }}</div>
      <div class="text-2xl font-bold">{{ summary.verifiedColumns }} / {{ summary.totalColumns }}</div>
      <div class="text-xs">{{ verifiedPct }}%</div>
    </SuccessContainer>
    <component
        :is="needsReviewCount > 0 ? ErrorContainer : SuccessContainer"
        class="!py-3"
    >
      <div class="text-xs uppercase">{{ t('adminDataTracking.needsReview') }}</div>
      <div class="text-2xl font-bold">{{ needsReviewCount }}</div>
    </component>
    <InfoContainer class="!py-3">
      <div class="text-xs uppercase">{{ t('adminDataTracking.schemaHash') }}</div>
      <div class="text-xs font-mono break-all">{{ schemaHash ?? '-' }}</div>
    </InfoContainer>
  </div>
</template>

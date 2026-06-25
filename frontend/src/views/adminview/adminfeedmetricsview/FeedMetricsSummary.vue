/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  totalRequests: number
  totalRendered: number
  totalCacheHits: number
  avgDurationMs: number
}>()

const {t, n} = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('feedMetrics.totalRequests') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totalRequests) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('feedMetrics.totalRendered') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totalRendered) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('feedMetrics.cacheHits') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totalCacheHits) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('feedMetrics.avgDuration') }}</MutedText>
      <div class="text-2xl font-semibold">{{ Math.round(avgDurationMs) }} ms</div>
    </NeutralContainer>
  </div>
</template>

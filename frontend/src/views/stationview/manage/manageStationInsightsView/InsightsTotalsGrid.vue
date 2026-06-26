/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  totalHits: number
  totalPages: number
  totalCount: number
}>()

const {t, n} = useI18n()
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('insights.totals.hits') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totalHits) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('insights.totals.pagesWithHits') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totalPages) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ t('insights.totals.publicPages') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totalCount) }}</div>
    </NeutralContainer>
  </div>
</template>

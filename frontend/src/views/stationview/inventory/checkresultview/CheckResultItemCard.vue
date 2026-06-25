/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { EnrichedCheckItem } from '@/api/types'

defineProps<{
  item: EnrichedCheckItem
}>()

const { t } = useI18n()

function resultLabel(result: string): string {
  switch (result) {
    case 'CONFIRMED': return t('inventory.check.resultConfirmed')
    case 'NOT_IN_POSSESSION': return t('inventory.check.resultNotInPossession')
    case 'LOST': return t('inventory.check.resultLost')
    default: return result
  }
}

function resultClass(result: string): string {
  switch (result) {
    case 'CONFIRMED': return 'bg-success/10 border-success'
    case 'NOT_IN_POSSESSION': return 'bg-info/10 border-info'
    case 'LOST': return 'bg-error/10 border-error'
    default: return ''
  }
}
</script>

<template>
  <NeutralContainer
    class="border-l-4 transition-all"
    :class="resultClass(item.result)"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="flex-1 min-w-0">
        <div v-if="item.itemName" class="font-medium text-sm">
          {{ item.itemName }}
          <SizeBadge>{{ item.sizeName ?? t('common.unisize') }}</SizeBadge>
        </div>
        <div class="text-xs text-(--text-muted)">
          {{ item.inventoryName }}
          <template v-if="item.internalId"> &middot; {{ item.internalId }}</template>
        </div>
      </div>
      <SuccessBadge v-if="item.result === 'CONFIRMED'" class="self-start shrink-0">
        {{ resultLabel(item.result) }}
      </SuccessBadge>
      <InfoBadge v-else-if="item.result === 'NOT_IN_POSSESSION'" class="self-start shrink-0">
        {{ resultLabel(item.result) }}
      </InfoBadge>
      <ErrorBadge v-else-if="item.result === 'LOST'" class="self-start shrink-0">
        {{ resultLabel(item.result) }}
      </ErrorBadge>
    </div>
    <MutedText tag="p" size="sm" class="mt-1" v-if="item.note">{{ item.note }}</MutedText>
  </NeutralContainer>
</template>

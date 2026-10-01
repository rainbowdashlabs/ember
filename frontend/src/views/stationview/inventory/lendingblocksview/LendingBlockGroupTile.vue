/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {formatDate} from '@/util/format'
import type {BlockInventory, GroupedBlock} from './types'

/**
 * One block on the list: its period, whether it stops all lending or which inventories and pieces
 * it holds back, and its reason. Lifting it is the screen's business, so the delete button only
 * says it was pressed.
 */
defineProps<{
  group: GroupedBlock
}>()

const emit = defineEmits<{
  delete: []
}>()

const {t} = useI18n()

function itemLabel(item: BlockInventory['items'][number]): string {
  if (item.name && item.internalId) return `${item.name} (${item.internalId})`
  return item.name || item.internalId || `#${item.id}`
}
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ formatDate(group.blockFrom) }} – {{ formatDate(group.blockTo) }}</span>
          <PrimaryBadge v-if="group.isFullBlock">{{ t('lending.blockScopeAll') }}</PrimaryBadge>
        </div>

        <div v-if="group.inventories.length > 0" class="mt-2 space-y-1">
          <div v-for="inv in group.inventories" :key="inv.inventoryId" class="flex items-center gap-2 text-xs">
            <SecondaryBadge>{{ inv.inventoryName || `#${inv.inventoryId}` }}</SecondaryBadge>
            <MutedText v-if="inv.allItems" size="sm">{{ t('lending.blockItemAll') }}</MutedText>
            <MutedText v-else size="sm">{{ inv.items.map(i => itemLabel(i)).join(', ') }}</MutedText>
          </div>
        </div>

        <MutedText v-if="group.reason" tag="div" size="sm" class="mt-1">{{ group.reason }}</MutedText>
      </div>
      <DeleteButton @click="emit('delete')"/>
    </div>
  </NeutralContainer>
</template>

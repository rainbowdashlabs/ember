/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckItemActions from './CheckItemActions.vue'
import type { InventoryItem } from '@/api/inventory'
import type { CheckResult, RequiredInventoryItem } from '@/api/inventoryCheck'

const props = defineProps<{
  item: InventoryItem
  req: RequiredInventoryItem
  result?: CheckResult
  note: string
  procurementCreated: boolean
  sizeLabel: string
}>()

const emit = defineEmits<{
  setResult: [itemId: number, result: CheckResult]
  setNote: [itemId: number, note: string]
  unassign: [itemId: number]
  createProcurement: [item: InventoryItem]
  correct: [item: InventoryItem, req: RequiredInventoryItem]
}>()

const { t } = useI18n()

function resultClass(): string {
  if (!props.result) return ''
  switch (props.result) {
    case 'CONFIRMED': return 'ring-2 ring-success bg-success/10'
    case 'NOT_IN_POSSESSION': return 'ring-2 ring-info bg-info/10'
    case 'LOST': return 'ring-2 ring-error bg-error/10'
    default: return ''
  }
}
</script>

<template>
  <div
    class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 space-y-2 transition-all"
    :class="resultClass()"
  >
    <!-- Item info + action buttons -->
    <div class="flex flex-col sm:flex-row sm:items-center gap-2">
      <div class="flex-1 min-w-0">
        <div class="font-medium text-sm truncate">
          {{ item.name }}
          <SizeBadge v-if="sizeLabel">{{ sizeLabel }}</SizeBadge>
        </div>
        <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
      </div>
      <CheckItemActions
        :item="item"
        :procurement-created="procurementCreated"
        :req="req"
        :result="result"
        @set-result="(id, r) => emit('setResult', id, r)"
        @unassign="id => emit('unassign', id)"
        @create-procurement="piece => emit('createProcurement', piece)"
        @correct="(piece, r) => emit('correct', piece, r)"
      />
    </div>

    <!-- Note -->
    <TextInput
      :model-value="note"
      :placeholder="t('inventory.check.notePlaceholder')"
      @update:model-value="emit('setNote', item.id, ($event as string) ?? '')"
    />

  </div>
</template>

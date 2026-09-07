/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type {InventoryItem} from '@/api/inventory'

/**
 * The pieces on offer to move, each saying what will happen to its size.
 *
 * <p>The size list belongs to the inventory being left, so a piece keeps a size of the same name in
 * the new list and arrives without one where there is none. Saying so before the move is what stops
 * the loss being a surprise afterwards.
 */
const props = defineProps<{
  items: InventoryItem[]
  selected: Set<number>
  sizeLabel: (item: InventoryItem) => string
  targetSizeLabels: Set<string>
  targetChosen: boolean
}>()

const emit = defineEmits<{
  toggle: [itemId: number]
  toggleAll: []
}>()

const {t} = useI18n()

/** What the piece will carry once it is over there, in the reader's terms. */
function sizeFate(item: InventoryItem): string {
  const label = props.sizeLabel(item)
  if (!label) return ''
  if (!props.targetChosen) return label
  return props.targetSizeLabels.has(label)
      ? t('inventory.move.sizeKept', {size: label})
      : t('inventory.move.sizeCleared', {size: label})
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center gap-3">
      <CheckboxInput
          :model-value="items.length > 0 && selected.size === items.length"
          data-testid="move-select-all"
          @update:model-value="emit('toggleAll')"
      />
      <span class="text-sm font-medium">{{ t('inventory.move.selectAll') }}</span>
    </div>
    <ul class="divide-y divide-(--border)">
      <li v-for="item in items" :key="item.id" class="flex items-center gap-3 py-2">
        <CheckboxInput
            :model-value="selected.has(item.id)"
            @update:model-value="emit('toggle', item.id)"
        />
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm">{{ item.name }}</p>
          <p class="truncate text-xs text-(--text-muted)">
            <span v-if="item.internalId">{{ item.internalId }}</span>
            <span v-if="item.internalId && sizeFate(item)"> &middot; </span>
            <span v-if="sizeFate(item)">{{ sizeFate(item) }}</span>
          </p>
        </div>
      </li>
    </ul>
  </NeutralContainer>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { InventoryItem } from '@/api/inventory'
import type { CheckResult, RequiredInventoryItem } from '@/api/inventoryCheck'

const props = defineProps<{
  item: InventoryItem
  req: RequiredInventoryItem
  result?: CheckResult
  note: string
  procurementCreated: boolean
  availableItems: InventoryItem[]
  slotSelections: Map<string, string>
  sizeLabel: string
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
}>()

const emit = defineEmits<{
  setResult: [itemId: number, result: CheckResult]
  setNote: [itemId: number, note: string]
  unassign: [itemId: number]
  createProcurement: [item: InventoryItem]
  changeItem: [currentItemId: number]
  createAndChange: [currentItemId: number, req: RequiredInventoryItem]
  updateSelection: [key: string, value: string]
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
      <div class="flex gap-1 shrink-0">
        <SuccessButton
          class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
          :class="{ 'opacity-40': result && result !== 'CONFIRMED' }"
          @click="emit('setResult', item.id, 'CONFIRMED')"
        >
          <font-awesome-icon :icon="['fas', 'check']" />
        </SuccessButton>
        <ErrorButton
          class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
          :class="{ 'opacity-40': result && result !== 'LOST' }"
          @click="emit('setResult', item.id, 'LOST')"
        >
          <font-awesome-icon :icon="['fas', 'xmark']" />
        </ErrorButton>
        <SecondaryButton
          class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
          @click="emit('unassign', item.id)"
        >
          <font-awesome-icon :icon="['fas', 'right-from-bracket']" />
        </SecondaryButton>
        <SecondaryButton
          v-if="result === 'LOST' && !procurementCreated"
          class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
          @click="emit('createProcurement', item)"
        >
          <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-1" />
          {{ t('inventory.check.createProcurement') }}
        </SecondaryButton>
        <span v-if="procurementCreated" class="text-xs text-success">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
          {{ t('inventory.check.procurementCreated') }}
        </span>
      </div>
    </div>

    <!-- Note -->
    <TextInput
      :model-value="note"
      :placeholder="t('inventory.check.notePlaceholder')"
      @update:model-value="emit('setNote', item.id, ($event as string) ?? '')"
    />

    <!-- Change: pick from existing unassigned -->
    <div v-if="availableItems.length > 0" class="flex flex-col sm:flex-row gap-2">
      <SelectInput
        :model-value="slotSelections.get(`change-${item.id}`) ?? ''"
        class="flex-1"
        @update:model-value="(v: string | number | null | undefined) => emit('updateSelection', `change-${item.id}`, String(v ?? ''))"
      >
        <option value="" disabled>{{ t('inventory.check.change') }}...</option>
        <option v-for="avail in availableItems" :key="avail.id" :value="String(avail.id)">
          {{ itemLabel(avail, req) }}
        </option>
      </SelectInput>
      <PrimaryButton
        class="text-sm"
        :disabled="!slotSelections.get(`change-${item.id}`)"
        @click="emit('changeItem', item.id)"
      >
        {{ t('inventory.check.change') }}
      </PrimaryButton>
    </div>

    <!-- Change: create new item by size -->
    <div class="flex flex-col sm:flex-row gap-2">
      <SelectInput
        v-if="req.hasSizes && req.sizes.length > 0"
        :model-value="slotSelections.get(`create-change-${item.id}`) ?? ''"
        class="flex-1"
        @update:model-value="(v: string | number | null | undefined) => emit('updateSelection', `create-change-${item.id}`, String(v ?? ''))"
      >
        <option value="" disabled>{{ t('inventory.check.selectSize') }}</option>
        <option v-for="size in req.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
      <SecondaryButton
        class="text-sm"
        :disabled="req.hasSizes && req.sizes.length > 0 && !slotSelections.get(`create-change-${item.id}`)"
        @click="emit('createAndChange', item.id, req)"
      >
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('inventory.check.create') }}
      </SecondaryButton>
    </div>
  </div>
</template>

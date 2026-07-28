/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { InventoryItem } from '@/api/inventory'
import type { RequiredInventoryItem } from '@/api/inventoryCheck'

const props = defineProps<{
  req: RequiredInventoryItem
  slotIndex: number
  isNotInPossession: boolean
  availableItems: InventoryItem[]
  slotSelections: Map<string, string>
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
}>()

const emit = defineEmits<{
  toggleNotInPossession: [inventoryId: number, slotIndex: number]
  assignToSlot: [inventoryId: number, slotIndex: number]
  createAndAssign: [req: RequiredInventoryItem, slotIndex: number]
  updateSelection: [key: string, value: string]
}>()

const { t } = useI18n()
</script>

<template>
  <div
    class="rounded border-2 border-dashed p-3 space-y-2 transition-all"
    :class="isNotInPossession ? 'border-info ring-2 ring-info bg-info/10' : 'border-bg-light-accent dark:border-bg-dark-accent'"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <span class="text-sm text-(--text-muted)">{{ t('inventory.check.emptySlot') }}</span>
      <InfoButton
        class="text-xs px-2 py-1.5 sm:py-1 w-full sm:w-auto"
        :class="{ 'opacity-100': isNotInPossession, 'opacity-60': !isNotInPossession }"
        @click="emit('toggleNotInPossession', req.inventoryId, slotIndex)"
      >
        <font-awesome-icon :icon="['fas', 'ban']" class="mr-1" />
        {{ t('inventory.check.notInPossession') }}
      </InfoButton>
    </div>

    <!-- Assign existing unassigned item -->
    <div v-if="availableItems.length > 0" class="flex flex-col sm:flex-row gap-2">
      <SelectInput
        :model-value="slotSelections.get(`${req.inventoryId}-${slotIndex}`) ?? ''"
        class="flex-1"
        @update:model-value="(v: string | number | null | undefined) => emit('updateSelection', `${req.inventoryId}-${slotIndex}`, String(v ?? ''))"
      >
        <option value="" disabled>{{ t('inventory.check.selectItem') }}</option>
        <option v-for="avail in availableItems" :key="avail.id" :value="String(avail.id)">
          {{ itemLabel(avail, req) }}
        </option>
      </SelectInput>
      <PrimaryButton
        class="text-sm"
        :disabled="!slotSelections.get(`${req.inventoryId}-${slotIndex}`)"
        @click="emit('assignToSlot', req.inventoryId, slotIndex)"
      >
        {{ t('inventory.check.assign') }}
      </PrimaryButton>
    </div>

    <!-- Create new item on the fly -->
    <div class="flex flex-col sm:flex-row gap-2">
      <SelectInput
        v-if="req.hasSizes && req.sizes.length > 0"
        :model-value="slotSelections.get(`create-${req.inventoryId}-${slotIndex}`) ?? ''"
        class="flex-1"
        @update:model-value="(v: string | number | null | undefined) => emit('updateSelection', `create-${req.inventoryId}-${slotIndex}`, String(v ?? ''))"
      >
        <option value="" disabled>{{ t('inventory.check.selectSize') }}</option>
        <option v-for="size in req.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
      <SecondaryButton
        class="text-sm"
        :disabled="req.hasSizes && req.sizes.length > 0 && !slotSelections.get(`create-${req.inventoryId}-${slotIndex}`)"
        @click="emit('createAndAssign', req, slotIndex)"
      >
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('inventory.check.create') }}
      </SecondaryButton>
    </div>
  </div>
</template>

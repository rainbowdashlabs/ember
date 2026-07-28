/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import type { InventoryItem } from '@/api/inventory'
import type { CheckResult, RequiredInventoryItem } from '@/api/inventoryCheck'

export type CheckEntry =
  | { type: 'item'; item: InventoryItem; req: RequiredInventoryItem }
  | { type: 'slot'; req: RequiredInventoryItem; slotIndex: number }

const props = defineProps<{
  uncheckedEntries: CheckEntry[]
  availableForInventory: (inventoryId: number) => InventoryItem[]
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
  sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
}>()

const emit = defineEmits<{
  setResult: [result: CheckResult]
  markNotInPossession: []
  assign: [itemId: string]
  createAndAssign: [sizeId: string]
  skip: []
  done: []
}>()

const { t } = useI18n()

const rapidAssignSelection = ref('')
const rapidCreateSizeId = ref('')
const skippedKeys = ref<Set<string>>(new Set())
const preferredEntryKey = ref<string | null>(null)
const scanError = ref('')

function entryKey(entry: CheckEntry): string {
  return entry.type === 'item'
    ? `item-${entry.item.id}`
    : `slot-${entry.req.inventoryId}-${entry.slotIndex}`
}

const currentEntry = computed((): CheckEntry | null => {
  const entries = props.uncheckedEntries
  if (entries.length === 0) return null
  if (preferredEntryKey.value) {
    const preferred = entries.find(e => entryKey(e) === preferredEntryKey.value)
    if (preferred) return preferred
  }
  const next = entries.find(e => !skippedKeys.value.has(entryKey(e)))
  if (next) return next
  return entries[0] ?? null
})

function handleScan(value: string) {
  const query = normaliseScannedPayload(value)
  scanError.value = ''
  if (!query) return
  const match = props.uncheckedEntries.find(e => {
    if (e.type !== 'item') return false
    const id = e.item.internalId
    return id ? id.toLocaleUpperCase('en-US') === query : false
  })
  if (match) {
    preferredEntryKey.value = entryKey(match)
    skippedKeys.value = new Set([...skippedKeys.value].filter(k => k !== preferredEntryKey.value))
  } else {
    scanError.value = t('inventory.check.rapidScanNoMatch')
  }
}

function resetSelections() {
  rapidAssignSelection.value = ''
  rapidCreateSizeId.value = ''
  preferredEntryKey.value = null
  scanError.value = ''
}

function handleSetResult(result: CheckResult) {
  const entry = currentEntry.value
  emit('setResult', result)
  if (entry) skippedKeys.value.delete(entryKey(entry))
  resetSelections()
}

function handleMarkNotInPossession() {
  const entry = currentEntry.value
  emit('markNotInPossession')
  if (entry) skippedKeys.value.delete(entryKey(entry))
  resetSelections()
}

function handleAssign() {
  if (!rapidAssignSelection.value) return
  emit('assign', rapidAssignSelection.value)
  resetSelections()
}

function handleCreateAndAssign() {
  emit('createAndAssign', rapidCreateSizeId.value)
  resetSelections()
}

function skip() {
  const entry = currentEntry.value
  if (entry) {
    const next = new Set(skippedKeys.value)
    next.add(entryKey(entry))
    skippedKeys.value = next
  }
  resetSelections()
  emit('skip')
}

defineExpose({ currentEntry })
</script>

<template>
  <!-- Rapid check: assigned item -->
  <NeutralContainer v-if="currentEntry?.type === 'item'" class="space-y-4">
    <div class="text-center space-y-2">
      <p class="text-xs text-(--text-muted)">{{ t('inventory.check.rapidProgress', { current: uncheckedEntries.length }) }}</p>
      <SubHeader>{{ currentEntry.req.inventoryName }}</SubHeader>
      <p class="text-2xl font-bold">{{ currentEntry.item.name }}</p>
      <div class="flex items-center justify-center gap-2">
        <SizeBadge v-if="sizeLabel(currentEntry.req, currentEntry.item.sizeId)">{{ sizeLabel(currentEntry.req, currentEntry.item.sizeId) }}</SizeBadge>
        <span v-if="currentEntry.item.internalId" class="text-sm text-(--text-muted)">{{ currentEntry.item.internalId }}</span>
      </div>
    </div>
    <p v-if="scanError" class="text-center text-sm text-error">{{ scanError }}</p>
    <div class="flex justify-center gap-4">
      <SuccessButton :icon="['fas', 'check']" @click="handleSetResult('CONFIRMED')">
        {{ t('inventory.check.confirmed') }}
      </SuccessButton>
      <ErrorButton :icon="['fas', 'xmark']" @click="handleSetResult('LOST')">
        {{ t('inventory.check.lost') }}
      </ErrorButton>
    </div>
    <div class="flex justify-center gap-2">
      <SecondaryButton @click="skip">
        {{ t('inventory.check.skip') }}
      </SecondaryButton>
      <ScanButton mode="continuous" @decoded="handleScan"/>
    </div>
  </NeutralContainer>

  <!-- Rapid check: empty slot -->
  <NeutralContainer v-else-if="currentEntry?.type === 'slot'" class="space-y-4">
    <div class="text-center space-y-2">
      <p class="text-xs text-(--text-muted)">{{ t('inventory.check.rapidProgress', { current: uncheckedEntries.length }) }}</p>
      <SubHeader>{{ currentEntry.req.inventoryName }}</SubHeader>
      <p class="text-lg font-medium text-(--text-muted)">{{ t('inventory.check.missingItem') }}</p>
      <p class="text-sm text-(--text-muted)">
        {{ currentEntry.req.assignedQuantity }} / {{ currentEntry.req.requiredQuantity }}
      </p>
    </div>

    <!-- Assign from existing unassigned -->
    <div v-if="availableForInventory(currentEntry.req.inventoryId).length > 0" class="flex flex-col sm:flex-row gap-2 max-w-md mx-auto">
      <SelectInput v-model="rapidAssignSelection" class="flex-1">
        <option value="" disabled>{{ t('inventory.check.selectItem') }}</option>
        <option v-for="avail in availableForInventory(currentEntry.req.inventoryId)" :key="avail.id" :value="String(avail.id)">
          {{ itemLabel(avail, currentEntry.req) }}
        </option>
      </SelectInput>
      <PrimaryButton :disabled="!rapidAssignSelection" @click="handleAssign">
        {{ t('inventory.check.assign') }}
      </PrimaryButton>
    </div>

    <!-- Create new item -->
    <div class="flex flex-col sm:flex-row gap-2 max-w-md mx-auto">
      <SelectInput v-if="currentEntry.req.hasSizes && currentEntry.req.sizes.length > 0" v-model="rapidCreateSizeId" class="flex-1">
        <option value="" disabled>{{ t('inventory.check.selectSize') }}</option>
        <option v-for="size in currentEntry.req.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="currentEntry.req.hasSizes && currentEntry.req.sizes.length > 0 && !rapidCreateSizeId" @click="handleCreateAndAssign">
        {{ t('inventory.check.create') }}
      </SecondaryButton>
    </div>

    <p v-if="scanError" class="text-center text-sm text-error">{{ scanError }}</p>
    <div class="flex justify-center gap-4">
      <InfoButton :icon="['fas', 'ban']" @click="handleMarkNotInPossession">
        {{ t('inventory.check.notInPossession') }}
      </InfoButton>
    </div>
    <div class="flex justify-center gap-2">
      <SecondaryButton @click="skip">
        {{ t('inventory.check.skip') }}
      </SecondaryButton>
      <ScanButton mode="continuous" @decoded="handleScan"/>
    </div>
  </NeutralContainer>

  <!-- Rapid check: done -->
  <NeutralContainer v-else class="text-center py-4 space-y-2">
    <p class="text-lg font-medium">{{ t('inventory.check.rapidDone') }}</p>
    <SecondaryButton @click="emit('done')">{{ t('inventory.check.backToList') }}</SecondaryButton>
  </NeutralContainer>
</template>

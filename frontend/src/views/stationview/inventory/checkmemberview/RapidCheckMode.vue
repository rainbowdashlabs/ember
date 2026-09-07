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
import TextInput from '@/components/input/text/TextInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import type { InventoryItem, RequiredInventoryItem } from '@/api/inventory'
import type { CheckResult } from '@/api/inventoryCheck'
import type { CheckEntry } from '@/composables/useMemberCheck'

const props = defineProps<{
  uncheckedEntries: CheckEntry[]
  availableForInventory: (inventoryId: number) => InventoryItem[]
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
  sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
  /** What has been written down about each piece so far, so the walk shows the same note the list does. */
  itemNotes: Map<number, string>
  /** The step a piece is standing on when something is already running on it, null otherwise. */
  movementStep: (itemId: number) => string | null
}>()

const emit = defineEmits<{
  setResult: [result: CheckResult]
  setNote: [itemId: number, note: string]
  createProcurement: [req: RequiredInventoryItem, slotIndex: number, sizeId: string]
  exchange: [entry: CheckEntry]
  correct: [entry: CheckEntry]
  markNotInPossession: []
  assign: [itemId: string]
  createAndAssign: [sizeId: string]
  skip: []
  undoResult: [itemId: number]
  undoNotInPossession: [inventoryId: number, slotIndex: number]
  done: []
}>()

const { t } = useI18n()

const rapidAssignSelection = ref('')
const rapidCreateSizeId = ref('')
const skippedKeys = ref<Set<string>>(new Set())
const preferredEntryKey = ref<string | null>(null)
const scanError = ref('')

/**
 * How a piece was left behind, so that stepping back can put it the way it was.
 *
 * <p>A skipped piece never left the walk and only has to be offered again. A decided one did leave
 * it, so going back has to take the decision off it first, which is why what was decided is kept
 * here rather than worked out again from a list the piece is no longer in.
 */
type LeftBehind =
  | { kind: 'skipped', key: string }
  | { kind: 'item', key: string, itemId: number }
  | { kind: 'slot', key: string, inventoryId: number, slotIndex: number }

const walked = ref<LeftBehind[]>([])

function remember(entry: CheckEntry | null, skipped: boolean) {
  if (!entry) return
  const key = entryKey(entry)
  if (skipped) walked.value.push({ kind: 'skipped', key })
  else if (entry.type === 'item') walked.value.push({ kind: 'item', key, itemId: entry.item.id })
  else walked.value.push({ kind: 'slot', key, inventoryId: entry.req.inventoryId, slotIndex: entry.slotIndex })
}

/**
 * Puts the walk back on the piece it just left, undoing what was said about it.
 *
 * <p>Only what this walk decided is taken back, one step at a time, so a slip on the piece just
 * handled does not mean starting the whole check again.
 */
function goBack() {
  const previous = walked.value.pop()
  if (!previous) return
  if (previous.kind === 'skipped') {
    const next = new Set(skippedKeys.value)
    next.delete(previous.key)
    skippedKeys.value = next
  } else if (previous.kind === 'item') {
    emit('undoResult', previous.itemId)
  } else {
    emit('undoNotInPossession', previous.inventoryId, previous.slotIndex)
  }
  rapidAssignSelection.value = ''
  rapidCreateSizeId.value = ''
  scanError.value = ''
  preferredEntryKey.value = previous.key
}

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

/**
 * What is already running on the piece in hand.
 *
 * <p>A piece can only be on one movement at a time, so a swap asked for beside a running one is
 * refused. The walk says what is happening to the piece instead of offering a button that the
 * station would only turn down.
 */
const runningStep = computed(() => {
  const entry = currentEntry.value
  return entry?.type === 'item' ? props.movementStep(entry.item.id) : null
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
  remember(entry, false)
  emit('setResult', result)
  if (entry) skippedKeys.value.delete(entryKey(entry))
  resetSelections()
}

function handleMarkNotInPossession() {
  const entry = currentEntry.value
  remember(entry, false)
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

/**
 * Ordering the piece that would fill this slot.
 *
 * <p>What is asked for where the store has nothing that fits. It is not about anything having been
 * lost: the slot is simply empty and stays empty until something arrives, and the size beside it is
 * the one being ordered.
 */
function handleCreateProcurement() {
  const entry = currentEntry.value
  if (entry?.type !== 'slot') return
  emit('createProcurement', entry.req, entry.slotIndex, rapidCreateSizeId.value)
  resetSelections()
}

/**
 * What has been written down about the piece in hand.
 *
 * <p>The note belongs to a piece, so only an item entry has one. An empty slot is a gap rather than
 * a thing, and the completed check has nowhere to put a note about it.
 */
const currentNote = computed(() => {
  const entry = currentEntry.value
  return entry?.type === 'item' ? props.itemNotes.get(entry.item.id) ?? '' : ''
})

function writeNote(note: string) {
  const entry = currentEntry.value
  if (entry?.type !== 'item') return
  emit('setNote', entry.item.id, note)
}

function skip() {
  const entry = currentEntry.value
  remember(entry, true)
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
      <p class="text-2xl font-bold">
        <span v-if="currentEntry.total > 1" class="text-(--text-muted) tabular-nums">
          {{ currentEntry.position }}/{{ currentEntry.total }}
        </span>
        {{ currentEntry.item.name }}
      </p>
      <div class="flex items-center justify-center gap-2">
        <SizeBadge v-if="sizeLabel(currentEntry.req, currentEntry.item.sizeId)">{{ sizeLabel(currentEntry.req, currentEntry.item.sizeId) }}</SizeBadge>
        <span v-if="currentEntry.item.internalId" class="text-sm text-(--text-muted)">{{ currentEntry.item.internalId }}</span>
      </div>
    </div>
    <p v-if="scanError" class="text-center text-sm text-error">{{ scanError }}</p>
    <div class="max-w-md mx-auto">
      <TextInput
          :model-value="currentNote"
          :placeholder="t('inventory.check.notePlaceholder')"
          class="w-full"
          data-testid="rapid-note"
          @update:model-value="writeNote(($event as string) ?? '')"
      />
    </div>
    <div class="flex justify-center gap-4">
      <SuccessButton :icon="['fas', 'check']" @click="handleSetResult('CONFIRMED')">
        {{ t('inventory.check.confirmed') }}
      </SuccessButton>
      <ErrorButton :icon="['fas', 'xmark']" @click="handleSetResult('LOST')">
        {{ t('inventory.check.lost') }}
      </ErrorButton>
    </div>
    <p v-if="runningStep !== null" class="text-center text-sm text-(--text-muted)" data-testid="rapid-on-the-move">
      {{ t('inventory.check.onTheMove') }}<span v-if="runningStep"> ({{ runningStep }})</span>
    </p>
    <div class="flex flex-wrap justify-center gap-2">
      <InfoButton
          v-if="runningStep === null"
          :icon="['fas', 'right-left']"
          data-testid="rapid-exchange"
          @click="emit('exchange', currentEntry)"
      >
        {{ t('inventory.check.exchange') }}
      </InfoButton>
      <SecondaryButton
          :icon="['fas', 'pen']"
          data-testid="rapid-correct"
          @click="emit('correct', currentEntry)"
      >
        {{ t('inventory.check.correct.action') }}
      </SecondaryButton>
    </div>
    <div class="flex justify-center gap-2">
      <SecondaryButton v-if="walked.length > 0" :icon="['fas', 'arrow-left']" data-testid="rapid-back" @click="goBack">
        {{ t('inventory.check.previousItem') }}
      </SecondaryButton>
      <SecondaryButton data-testid="rapid-skip" @click="skip">
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
      <p class="text-lg font-medium text-(--text-muted)">
        <span v-if="currentEntry.total > 1" class="tabular-nums">{{ currentEntry.position }}/{{ currentEntry.total }}</span>
        {{ t('inventory.check.missingItem') }}
      </p>
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
      <SecondaryButton
          :icon="['fas', 'folder-plus']"
          data-testid="rapid-create-procurement"
          @click="handleCreateProcurement"
      >
        {{ t('inventory.check.createProcurement') }}
      </SecondaryButton>
    </div>

    <p v-if="scanError" class="text-center text-sm text-error">{{ scanError }}</p>
    <div class="flex justify-center gap-4">
      <InfoButton :icon="['fas', 'ban']" @click="handleMarkNotInPossession">
        {{ t('inventory.check.notInPossession') }}
      </InfoButton>
    </div>
    <div class="flex justify-center gap-2">
      <SecondaryButton v-if="walked.length > 0" :icon="['fas', 'arrow-left']" data-testid="rapid-back" @click="goBack">
        {{ t('inventory.check.previousItem') }}
      </SecondaryButton>
      <SecondaryButton data-testid="rapid-skip" @click="skip">
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

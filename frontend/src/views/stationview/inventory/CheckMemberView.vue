/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { CheckResult, InventoryItem, MemberCheckState, RequiredInventoryItem } from '@/api/types'
import { inventoryCheck, procurement } from '@/api'
import MemberName from '@/components/avatar/MemberName.vue'
import RapidCheckMode from './checkmemberview/RapidCheckMode.vue'
import type { CheckEntry } from './checkmemberview/RapidCheckMode.vue'
import InventorySection from './checkmemberview/InventorySection.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.memberId))
const teamOnly = computed(() => route.query.teamOnly === 'true')
const state = ref<MemberCheckState | null>(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

// Per-item check results and notes
const itemResults = ref<Map<number, CheckResult>>(new Map())
const itemNotes = ref<Map<number, string>>(new Map())
const procurementCreated = ref<Set<number>>(new Set())

// Check mode
const checkMode = ref(false)

// Per-slot assign selection (key: "inventoryId-slotIndex")
const slotSelections = ref<Map<string, string>>(new Map())

// Empty slots marked as "not in possession" (key: "inventoryId-slotIndex")
const slotsNotInPossession = ref<Set<string>>(new Set())

function setResult(itemId: number, result: CheckResult) {
  const current = itemResults.value.get(itemId)
  if (current === result) {
    itemResults.value.delete(itemId)
  } else {
    itemResults.value.set(itemId, result)
  }
  itemResults.value = new Map(itemResults.value)
}

function setNote(itemId: number, note: string) {
  itemNotes.value.set(itemId, note)
  itemNotes.value = new Map(itemNotes.value)
}

function assignedForInventory(inventoryId: number): InventoryItem[] {
  if (!state.value) return []
  return state.value.assigned.filter(i => i.inventoryId === inventoryId)
}

function availableForInventory(inventoryId: number): InventoryItem[] {
  if (!state.value) return []
  return state.value.unassigned[inventoryId] ?? []
}

function emptySlotCount(req: RequiredInventoryItem): number {
  return Math.max(0, req.requiredQuantity - req.assignedQuantity)
}

function toggleNotInPossession(inventoryId: number, slotIdx: number) {
  const key = `${inventoryId}-${slotIdx}`
  const newSet = new Set(slotsNotInPossession.value)
  if (newSet.has(key)) newSet.delete(key)
  else newSet.add(key)
  slotsNotInPossession.value = newSet
}

const allMarked = computed(() => {
  if (!state.value) return false
  const assignedMarked = state.value.assigned.every(item => itemResults.value.has(item.id))
  const emptyMarked = state.value.required.every(req => {
    const empty = emptySlotCount(req)
    for (let i = 1; i <= empty; i++) {
      if (!slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) return false
    }
    return true
  })
  return assignedMarked && emptyMarked
})

const uncheckedEntries = computed((): CheckEntry[] => {
  if (!state.value) return []
  const entries: CheckEntry[] = []
  for (const req of state.value.required) {
    for (const item of assignedForInventory(req.inventoryId)) {
      if (!itemResults.value.has(item.id)) {
        entries.push({ type: 'item', item, req })
      }
    }
    const empty = emptySlotCount(req)
    for (let i = 1; i <= empty; i++) {
      if (!slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) {
        entries.push({ type: 'slot', req, slotIndex: i })
      }
    }
  }
  return entries
})

function startCheckMode() {
  checkMode.value = true
}

function markAllConfirmed() {
  if (!state.value) return
  for (const item of state.value.assigned) {
    if (!itemResults.value.has(item.id)) {
      itemResults.value.set(item.id, 'CONFIRMED')
    }
  }
  itemResults.value = new Map(itemResults.value)
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    state.value = await inventoryCheck.startCheck(memberId.value)
  } catch (e: any) {
    if (e?.response?.status === 409) {
      error.value = t('inventory.check.locked')
    } else {
      error.value = t('common.error')
    }
  } finally {
    loading.value = false
  }
}

// Rapid check mode handlers
function onRapidSetResult(result: CheckResult) {
  const entry = uncheckedEntries.value[0]
  if (!entry || entry.type !== 'item') return
  itemResults.value.set(entry.item.id, result)
  itemResults.value = new Map(itemResults.value)
}

function onRapidMarkNotInPossession() {
  const entry = uncheckedEntries.value[0]
  if (!entry || entry.type !== 'slot') return
  toggleNotInPossession(entry.req.inventoryId, entry.slotIndex)
}

async function onRapidAssign(itemIdStr: string) {
  error.value = ''
  try {
    state.value = await inventoryCheck.assignItem(memberId.value, Number(itemIdStr))
  } catch {
    error.value = t('common.error')
  }
}

async function onRapidCreateAndAssign(sizeIdStr: string) {
  const entry = uncheckedEntries.value[0]
  if (!entry || entry.type !== 'slot') return
  error.value = ''
  try {
    state.value = await inventoryCheck.createAndAssign(
      memberId.value,
      entry.req.inventoryId,
      sizeIdStr ? Number(sizeIdStr) : null,
    )
  } catch {
    error.value = t('common.error')
  }
}

// Inventory section handlers
function updateSelection(key: string, value: string) {
  slotSelections.value.set(key, value)
  slotSelections.value = new Map(slotSelections.value)
}

async function assignToSlot(inventoryId: number, slotIndex: number) {
  const key = `${inventoryId}-${slotIndex}`
  const selectedId = slotSelections.value.get(key)
  if (!selectedId) return
  error.value = ''
  try {
    state.value = await inventoryCheck.assignItem(memberId.value, Number(selectedId))
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function changeItem(currentItemId: number, _inventoryId: number) {
  const key = `change-${currentItemId}`
  const selectedId = slotSelections.value.get(key)
  if (!selectedId) return
  error.value = ''
  try {
    itemResults.value.delete(currentItemId)
    itemResults.value = new Map(itemResults.value)
    state.value = await inventoryCheck.assignItem(memberId.value, Number(selectedId), currentItemId)
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function unassignItem(itemId: number) {
  if (!confirm(t('inventory.check.unassignConfirm'))) return
  error.value = ''
  try {
    itemResults.value.delete(itemId)
    itemNotes.value.delete(itemId)
    itemResults.value = new Map(itemResults.value)
    itemNotes.value = new Map(itemNotes.value)
    state.value = await inventoryCheck.unassignItem(memberId.value, itemId)
  } catch {
    error.value = t('common.error')
  }
}

async function createAndChangeItem(currentItemId: number, req: RequiredInventoryItem) {
  const key = `create-change-${currentItemId}`
  const selectedSizeId = slotSelections.value.get(key)
  error.value = ''
  try {
    itemResults.value.delete(currentItemId)
    itemResults.value = new Map(itemResults.value)
    state.value = await inventoryCheck.createAndAssign(
      memberId.value,
      req.inventoryId,
      selectedSizeId ? Number(selectedSizeId) : null,
      currentItemId,
    )
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function createAndAssignToSlot(req: RequiredInventoryItem, slotIndex: number) {
  const key = `create-${req.inventoryId}-${slotIndex}`
  const selectedSizeId = slotSelections.value.get(key)
  error.value = ''
  try {
    state.value = await inventoryCheck.createAndAssign(
      memberId.value,
      req.inventoryId,
      selectedSizeId ? Number(selectedSizeId) : null,
    )
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function createProcurementForItem(item: InventoryItem) {
  try {
    await procurement.createProcurement({
      inventoryId: item.inventoryId,
      memberId: memberId.value,
      sizeId: item.sizeId ?? undefined,
      notes: itemNotes.value.get(item.id) || undefined,
    })
    procurementCreated.value = new Set([...procurementCreated.value, item.id])
  } catch { /* ignore */ }
}

async function submit() {
  if (!state.value || !allMarked.value) return
  submitting.value = true
  error.value = ''
  try {
    const items: import('@/api/types').CheckItemResult[] = state.value.assigned.map(item => ({
      itemId: item.id,
      result: itemResults.value.get(item.id)!,
      note: itemNotes.value.get(item.id) ?? '',
    }))
    for (const req of state.value.required) {
      const empty = emptySlotCount(req)
      for (let i = 1; i <= empty; i++) {
        if (slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) {
          items.push({ inventoryId: req.inventoryId, result: 'NOT_IN_POSSESSION', note: '' })
        }
      }
    }
    const completedMemberId = memberId.value
    await inventoryCheck.completeCheck(completedMemberId, { items })

    const nextId = await inventoryCheck.getNextMember(completedMemberId, teamOnly.value)
    if (nextId) {
      state.value = null
      itemResults.value = new Map()
      itemNotes.value = new Map()
      slotSelections.value = new Map()
      slotsNotInPossession.value = new Set()
      await router.replace({ name: 'inventory-check-member', params: { memberId: nextId }, query: { teamOnly: teamOnly.value ? 'true' : 'false' } })
      await loadData()
    } else {
      await router.push({ name: 'inventory-checks' })
    }
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}

async function cancel() {
  try {
    await inventoryCheck.cancelCheck(memberId.value)
  } catch {
    // ignore
  }
  router.push({ name: 'inventory-checks' })
}

function sizeLabel(req: RequiredInventoryItem, sizeId?: number | null): string {
  if (!sizeId || !req.sizes.length) return ''
  return req.sizes.find(s => s.id === sizeId)?.label ?? ''
}

function itemLabel(item: InventoryItem, req: RequiredInventoryItem): string {
  const parts = [item.name]
  if (item.internalId) parts.push(`(${item.internalId})`)
  const size = sizeLabel(req, item.sizeId)
  if (size) parts.push(size)
  return parts.join(' ')
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && state">
        <!-- Header -->
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div>
            <SectionHeader><MemberName :identity="null" size="md"/></SectionHeader>
            <p class="text-sm text-(--text-muted)">{{ t('inventory.check.title') }}</p>
          </div>
          <div class="flex gap-2">
            <PrimaryButton :icon="['fas', 'list-check']" v-if="uncheckedEntries.length > 0 && !checkMode" class="text-sm" @click="startCheckMode">
              {{ t('inventory.check.rapidCheck') }}
            </PrimaryButton>
            <SecondaryButton v-if="state.assigned.length > 0 && !checkMode" class="text-sm" @click="markAllConfirmed">
              {{ t('inventory.check.markAll') }}
            </SecondaryButton>
            <SecondaryButton @click="cancel">{{ t('inventory.check.cancel') }}</SecondaryButton>
          </div>
        </div>

        <!-- Last check info -->
        <NeutralContainer v-if="state.lastCheck">
          <div class="text-sm text-(--text-muted)">
            {{ t('inventory.check.lastChecked') }}: {{ new Date(state.lastCheck.checkedAt).toLocaleString('de-DE') }}
          </div>
        </NeutralContainer>

        <!-- Rapid check mode -->
        <RapidCheckMode
          v-if="checkMode"
          :unchecked-entries="uncheckedEntries"
          :available-for-inventory="availableForInventory"
          :item-label="itemLabel"
          :size-label="sizeLabel"
          @set-result="onRapidSetResult"
          @mark-not-in-possession="onRapidMarkNotInPossession"
          @assign="onRapidAssign"
          @create-and-assign="onRapidCreateAndAssign"
          @skip="() => {}"
          @done="checkMode = false"
        />

        <!-- Inventory sections -->
        <div v-if="!checkMode" class="space-y-6">
          <InventorySection
            v-for="req in state.required"
            :key="req.inventoryId"
            :req="req"
            :assigned-items="assignedForInventory(req.inventoryId)"
            :available-items="availableForInventory(req.inventoryId)"
            :empty-slot-count="emptySlotCount(req)"
            :item-results="itemResults"
            :item-notes="itemNotes"
            :procurement-created="procurementCreated"
            :slots-not-in-possession="slotsNotInPossession"
            :slot-selections="slotSelections"
            :size-label="sizeLabel"
            :item-label="itemLabel"
            @set-result="setResult"
            @set-note="setNote"
            @unassign="unassignItem"
            @create-procurement="createProcurementForItem"
            @change-item="changeItem"
            @create-and-change="createAndChangeItem"
            @toggle-not-in-possession="toggleNotInPossession"
            @assign-to-slot="assignToSlot"
            @create-and-assign-to-slot="createAndAssignToSlot"
            @update-selection="updateSelection"
          />
        </div>

        <!-- Submit (sticky on mobile) -->
        <div class="sticky bottom-0 bg-bg-light dark:bg-bg-dark py-4 -mx-4 px-4 sm:mx-0 sm:px-0 sm:relative border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50 sm:border-0 flex justify-end gap-3">
          <SecondaryButton @click="cancel">{{ t('inventory.check.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!allMarked || submitting" @click="submit">
            {{ submitting ? t('inventory.check.submitting') : t('inventory.check.complete') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>

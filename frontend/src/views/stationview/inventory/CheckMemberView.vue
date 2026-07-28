/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type { InventoryItem } from '@/api/inventory'
import type { CheckResult, MemberCheckState, RequiredInventoryItem } from '@/api/inventoryCheck'
import { inventoryCheck, procurement } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useAsyncAction } from '@/composables/useAsyncAction'
import type { CheckEntry } from './checkmemberview/RapidCheckMode.vue'
import CheckMemberBody from './checkmemberview/CheckMemberBody.vue'

const bodyRef = ref<InstanceType<typeof CheckMemberBody> | null>(null)

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.memberId))
const teamOnly = computed(() => route.query.teamOnly === 'true')
const {config: state, loading, error, reload: loadData} = useConfigPanel<MemberCheckState | null>({
  initial: null,
  fetch: () => inventoryCheck.startCheck(memberId.value),
  formatError: (e: any) => e?.response?.status === 409 ? t('inventory.check.locked') : t('common.error'),
})

const itemResults = ref<Map<number, CheckResult>>(new Map())
const itemNotes = ref<Map<number, string>>(new Map())
const procurementCreated = ref<Set<number>>(new Set())

const checkMode = ref(false)

const slotSelections = ref<Map<string, string>>(new Map())

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

function currentRapidEntry(): CheckEntry | null {
  return bodyRef.value?.getCurrentRapidEntry() ?? null
}

function onRapidSetResult(result: CheckResult) {
  const entry = currentRapidEntry()
  if (!entry || entry.type !== 'item') return
  itemResults.value.set(entry.item.id, result)
  itemResults.value = new Map(itemResults.value)
}

function onRapidMarkNotInPossession() {
  const entry = currentRapidEntry()
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
  const entry = currentRapidEntry()
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
  } catch {
  }
}

const {running: submitting, run: submit} = useAsyncAction(async () => {
  if (!state.value || !allMarked.value) return
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
  }
})

async function cancel() {
  try {
    await inventoryCheck.cancelCheck(memberId.value)
  } catch {
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
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-check-member.title')"
      :subtitle="t('pages.inventory-check-member.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <CheckMemberBody
        v-if="!loading && state"
        ref="bodyRef"
        :state="state"
        :check-mode="checkMode"
        :unchecked-entries="uncheckedEntries"
        :all-marked="allMarked"
        :submitting="submitting"
        :item-results="itemResults"
        :item-notes="itemNotes"
        :procurement-created="procurementCreated"
        :slots-not-in-possession="slotsNotInPossession"
        :slot-selections="slotSelections"
        :assigned-for-inventory="assignedForInventory"
        :available-for-inventory="availableForInventory"
        :empty-slot-count="emptySlotCount"
        :size-label="sizeLabel"
        :item-label="itemLabel"
        @start-check-mode="startCheckMode"
        @mark-all-confirmed="markAllConfirmed"
        @cancel="cancel"
        @submit="submit"
        @rapid-set-result="onRapidSetResult"
        @rapid-mark-not-in-possession="onRapidMarkNotInPossession"
        @rapid-assign="onRapidAssign"
        @rapid-create-and-assign="onRapidCreateAndAssign"
        @rapid-done="checkMode = false"
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
  </ViewContent>
</template>

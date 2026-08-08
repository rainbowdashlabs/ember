/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InventoryItem } from '@/api/inventory'
import type { CheckResult, MemberCheckState, RequiredInventoryItem } from '@/api/inventoryCheck'
import { inventoryCheck, procurement } from '@/api'
import { reportCaughtError } from '@/util/devErrorReporter'

/**
 * One thing still to be looked at during a check: either an item the member holds, or an empty
 * slot for an item they should hold but do not.
 */
export type CheckEntry =
  | { type: 'item'; item: InventoryItem; req: RequiredInventoryItem }
  | { type: 'slot'; req: RequiredInventoryItem; slotIndex: number }

/**
 * The working state of one member's inventory check: what has been marked, what is missing, and
 * every operation that changes an assignment while the check is open.
 *
 * The check is edited entirely in the browser and only persisted when it is completed, so the
 * marks live here rather than on the server. Each map is replaced rather than mutated so Vue sees
 * the change.
 *
 * @param memberId the member being checked
 * @param state    the server-side check state, replaced by every assignment operation
 * @param error    the view's error channel, set when an operation fails
 */
export function useMemberCheck(
  memberId: Ref<number>,
  state: Ref<MemberCheckState | null>,
  error: Ref<string>,
) {
  const { t } = useI18n()

  const itemResults = ref<Map<number, CheckResult>>(new Map())
  const itemNotes = ref<Map<number, string>>(new Map())
  const procurementCreated = ref<Set<number>>(new Set())
  const slotSelections = ref<Map<string, string>>(new Map())
  const slotsNotInPossession = ref<Set<string>>(new Set())

  /**
   * Runs an assignment operation, replacing the check state with its result and reporting a
   * failure through the view's error channel rather than throwing.
   */
  async function apply(operation: () => Promise<MemberCheckState>, onSuccess?: () => void) {
    error.value = ''
    try {
      state.value = await operation()
      onSuccess?.()
    } catch {
      error.value = t('common.error')
    }
  }

  function takeSelection(key: string): string | undefined {
    const selected = slotSelections.value.get(key)
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
    return selected
  }

  function forgetResult(itemId: number) {
    itemResults.value.delete(itemId)
    itemResults.value = new Map(itemResults.value)
  }

  function setResult(itemId: number, result: CheckResult) {
    if (itemResults.value.get(itemId) === result) {
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

  function updateSelection(key: string, value: string) {
    slotSelections.value.set(key, value)
    slotSelections.value = new Map(slotSelections.value)
  }

  function assignedForInventory(inventoryId: number): InventoryItem[] {
    return state.value?.assigned.filter(i => i.inventoryId === inventoryId) ?? []
  }

  function availableForInventory(inventoryId: number): InventoryItem[] {
    return state.value?.unassigned[inventoryId] ?? []
  }

  function emptySlotCount(req: RequiredInventoryItem): number {
    return Math.max(0, req.requiredQuantity - req.assignedQuantity)
  }

  function toggleNotInPossession(inventoryId: number, slotIdx: number) {
    const key = `${inventoryId}-${slotIdx}`
    const next = new Set(slotsNotInPossession.value)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    slotsNotInPossession.value = next
  }

  const allMarked = computed(() => {
    if (!state.value) return false
    const assignedMarked = state.value.assigned.every(item => itemResults.value.has(item.id))
    const emptyMarked = state.value.required.every(req => {
      for (let i = 1; i <= emptySlotCount(req); i++) {
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
        if (!itemResults.value.has(item.id)) entries.push({ type: 'item', item, req })
      }
      for (let i = 1; i <= emptySlotCount(req); i++) {
        if (!slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) {
          entries.push({ type: 'slot', req, slotIndex: i })
        }
      }
    }
    return entries
  })

  function markAllConfirmed() {
    if (!state.value) return
    for (const item of state.value.assigned) {
      if (!itemResults.value.has(item.id)) itemResults.value.set(item.id, 'CONFIRMED')
    }
    itemResults.value = new Map(itemResults.value)
  }

  async function assignItem(itemId: number) {
    await apply(() => inventoryCheck.assignItem(memberId.value, itemId))
  }

  async function createAndAssign(inventoryId: number, sizeId: number | null) {
    await apply(() => inventoryCheck.createAndAssign(memberId.value, inventoryId, sizeId))
  }

  async function assignToSlot(inventoryId: number, slotIndex: number) {
    const key = `${inventoryId}-${slotIndex}`
    const selected = slotSelections.value.get(key)
    if (!selected) return
    await apply(() => inventoryCheck.assignItem(memberId.value, Number(selected)), () => takeSelection(key))
  }

  async function changeItem(currentItemId: number) {
    const key = `change-${currentItemId}`
    const selected = slotSelections.value.get(key)
    if (!selected) return
    forgetResult(currentItemId)
    await apply(
      () => inventoryCheck.assignItem(memberId.value, Number(selected), currentItemId),
      () => takeSelection(key),
    )
  }

  async function createAndChangeItem(currentItemId: number, req: RequiredInventoryItem) {
    const key = `create-change-${currentItemId}`
    const sizeId = slotSelections.value.get(key)
    forgetResult(currentItemId)
    await apply(
      () =>
        inventoryCheck.createAndAssign(
          memberId.value,
          req.inventoryId,
          sizeId ? Number(sizeId) : null,
          currentItemId,
        ),
      () => takeSelection(key),
    )
  }

  async function createAndAssignToSlot(req: RequiredInventoryItem, slotIndex: number) {
    const key = `create-${req.inventoryId}-${slotIndex}`
    const sizeId = slotSelections.value.get(key)
    await apply(
      () => inventoryCheck.createAndAssign(memberId.value, req.inventoryId, sizeId ? Number(sizeId) : null),
      () => takeSelection(key),
    )
  }

  async function unassignItem(itemId: number) {
    forgetResult(itemId)
    itemNotes.value.delete(itemId)
    itemNotes.value = new Map(itemNotes.value)
    await apply(() => inventoryCheck.unassignItem(memberId.value, itemId))
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
    } catch (e) {
      reportCaughtError(e, 'procurement creation during member check')
    }
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

  /**
   * Drops every mark so the next member starts from a clean check.
   */
  function reset() {
    itemResults.value = new Map()
    itemNotes.value = new Map()
    slotSelections.value = new Map()
    slotsNotInPossession.value = new Set()
  }

  return {
    itemResults,
    itemNotes,
    procurementCreated,
    slotSelections,
    slotsNotInPossession,
    allMarked,
    uncheckedEntries,
    setResult,
    setNote,
    updateSelection,
    assignedForInventory,
    availableForInventory,
    emptySlotCount,
    toggleNotInPossession,
    markAllConfirmed,
    assignItem,
    createAndAssign,
    assignToSlot,
    changeItem,
    createAndChangeItem,
    createAndAssignToSlot,
    unassignItem,
    createProcurementForItem,
    sizeLabel,
    itemLabel,
    reset,
  }
}

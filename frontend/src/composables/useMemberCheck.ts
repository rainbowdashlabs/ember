/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InventoryItem, RequiredInventoryItem } from '@/api/inventory'
import type { CheckResult, CorrectItemRequest, MemberCheckState } from '@/api/inventoryCheck'
import { inventoryCheck, procurement } from '@/api'
import { showToast } from '@/util/toast'

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

  /** The slots already ordered for, as `inventory-slot`, so the offer is not made twice. */
  const slotProcurements = ref<Set<string>>(new Set())
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

  /**
   * Writes down what the member is really holding, in place of what the record said.
   *
   * <p>The mark on the piece coming off the record goes with it: it was a mark about something the
   * member never had, and leaving it behind would count a piece nobody is looking at any more.
   */
  async function correctItem(payload: CorrectItemRequest) {
    if (payload.oldItemId) forgetResult(payload.oldItemId)
    await apply(() => inventoryCheck.correctItem(memberId.value, payload))
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

  /**
   * Orders the piece that would fill an empty slot, and settles the slot with it.
   *
   * <p>Raised where the store holds nothing that fits, which is what a procurement is for. Nothing
   * has been lost here: the member is simply owed something that is not there.
   *
   * <p>The slot is marked as one they do not have, because that is what it is and what the order has
   * just confirmed. Without it the walk would offer the same empty slot again, with the order already
   * placed behind it.
   */
  async function createProcurementForSlot(req: RequiredInventoryItem, slotIndex: number, sizeId?: number) {
    error.value = ''
    try {
      await procurement.createProcurement({
        inventoryId: req.inventoryId,
        memberId: memberId.value,
        sizeId,
      })
      const key = `${req.inventoryId}-${slotIndex}`
      slotProcurements.value = new Set([...slotProcurements.value, key])
      if (!slotsNotInPossession.value.has(key)) {
        slotsNotInPossession.value = new Set([...slotsNotInPossession.value, key])
      }
      showToast(t('inventory.check.procurementNoted'), 'success')
    } catch {
      error.value = t('common.error')
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
    correctItem,
    createAndAssignToSlot,
    unassignItem,
    createProcurementForSlot,
    slotProcurements,
    sizeLabel,
    itemLabel,
    reset,
  }
}

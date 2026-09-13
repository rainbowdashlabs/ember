/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MemberRequirements, MyInventoryItem } from '@/api/inventory'
import { inventory, movements } from '@/api'

/**
 * Owns the equipment handed out to the viewed member: the assigned items, what is still missing, and
 * the assignment actions performed on them.
 *
 * <p>Nothing here reads movements. Whatever is running on a piece travels with the piece, so the rows
 * say where they stand without a second list to join them against.
 */
export function useMemberInventory(memberId: Ref<number>, error: Ref<string>) {
  const { t } = useI18n()

  const items = ref<MyInventoryItem[]>([])
  const requirements = ref<MemberRequirements>({ required: [], unassigned: {} })

  async function loadRequirements() {
    try {
      requirements.value = await inventory.memberRequirements(memberId.value)
    } catch {
      requirements.value = { required: [], unassigned: {} }
    }
  }

  async function load() {
    try { items.value = await inventory.memberItems(memberId.value) } catch { items.value = [] }
    await loadRequirements()
  }

  async function assignItem(itemId: number) {
    error.value = ''
    try {
      await inventory.assignItem(itemId, { memberId: memberId.value })
      items.value = await inventory.memberItems(memberId.value)
      await loadRequirements()
    } catch { error.value = t('common.error') }
  }

  /** Writes down that the member is to get this piece, leaving it where it is until it is handed over. */
  async function planHandOut(itemId: number, inventoryId: number) {
    error.value = ''
    try {
      await movements.planHandOut(memberId.value, itemId, inventoryId)
      await load()
    } catch { error.value = t('inventory.handOut.plannedFailed') }
  }

  /** Writes a new piece down in the inventory that is short and hands it to the member at once. */
  async function handOutNewItem(inventoryId: number, sizeId: number | null) {
    error.value = ''
    try {
      await inventory.handOutNewItem(memberId.value, inventoryId, sizeId)
      items.value = await inventory.memberItems(memberId.value)
      await loadRequirements()
    } catch { error.value = t('common.error') }
  }

  async function unassignItem(item: MyInventoryItem) {
    error.value = ''
    try {
      await inventory.assignItem(item.id, { memberId: null })
      items.value = await inventory.memberItems(memberId.value)
      await loadRequirements()
    } catch { error.value = t('common.error') }
  }

  async function reassignItem(itemId: number, targetMemberId: number) {
    error.value = ''
    try {
      await inventory.assignItem(itemId, { memberId: targetMemberId })
      items.value = await inventory.memberItems(memberId.value)
    } catch { error.value = t('common.error') }
  }

  return {
    items,
    requirements,
    load,
    assignItem,
    planHandOut,
    handOutNewItem,
    unassignItem,
    reassignItem,
  }
}

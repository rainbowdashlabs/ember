/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MemberRequirements, MyInventoryItem } from '@/api/inventory'
import { inventory, movements } from '@/api'
import { describeFailure, type Failure } from '@/util/failure'

/**
 * Owns the equipment handed out to the viewed member: the assigned items, what is still missing, and
 * the assignment actions performed on them.
 *
 * <p>Nothing here reads movements. Whatever is running on a piece travels with the piece, so the rows
 * say where they stand without a second list to join them against.
 */
export function useMemberInventory(memberId: Ref<number>, failure: Ref<Failure | null>) {
  const { t } = useI18n()

  const items = ref<MyInventoryItem[]>([])
  const requirements = ref<MemberRequirements>({ required: [], unassigned: {} })

  async function fetchAll() {
    items.value = await inventory.memberItems(memberId.value)
    requirements.value = await inventory.memberRequirements(memberId.value)
  }

  /**
   * Reads what the member holds and what they are still owed.
   *
   * <p>A failure here used to leave both lists empty without a word, and an empty gear list is a
   * statement: it says this member holds nothing, which is the one thing the tab exists to say.
   */
  async function load() {
    failure.value = null
    try {
      await fetchAll()
    } catch (e) {
      items.value = []
      requirements.value = { required: [], unassigned: {} }
      failure.value = {...describeFailure(e, t), message: t('memberDetail.inventoryUnreadable')}
    }
  }

  /**
   * Reads the lists again after something was handed over or taken back.
   *
   * <p>Caught apart from the write itself: by the time this runs the piece has already changed hands,
   * and a reader told the handover failed hands it over a second time.
   */
  async function refreshAfterWrite() {
    try {
      await fetchAll()
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    }
  }

  async function write(operation: () => Promise<unknown>, message?: string) {
    failure.value = null
    try {
      await operation()
    } catch (e) {
      const described = describeFailure(e, t)
      failure.value = message ? {...described, message} : described
      return
    }
    await refreshAfterWrite()
  }

  async function assignItem(itemId: number) {
    await write(() => inventory.assignItem(itemId, { memberId: memberId.value }))
  }

  /** Writes down that the member is to get this piece, leaving it where it is until it is handed over. */
  async function planHandOut(itemId: number, inventoryId: number) {
    await write(
        () => movements.planHandOut(memberId.value, itemId, inventoryId),
        t('inventory.handOut.plannedFailed'))
  }

  /** Writes a new piece down in the inventory that is short and hands it to the member at once. */
  async function handOutNewItem(inventoryId: number, sizeId: number | null) {
    await write(() => inventory.handOutNewItem(memberId.value, inventoryId, sizeId))
  }

  async function unassignItem(item: MyInventoryItem) {
    await write(() => inventory.assignItem(item.id, { memberId: null }))
  }

  async function reassignItem(itemId: number, targetMemberId: number) {
    await write(() => inventory.assignItem(itemId, { memberId: targetMemberId }))
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

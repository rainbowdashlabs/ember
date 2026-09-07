/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InventorySize, MemberRequirements, MyInventoryItem } from '@/api/inventory'
import {stillMoving, type ExchangeRequestEntry} from '@/api/exchanges'
import { exchanges, inventory } from '@/api'

/**
 * Owns the equipment handed out to the viewed member: the assigned items, the
 * open exchange requests and the assignment actions performed on them.
 */
export function useMemberInventory(memberId: Ref<number>, error: Ref<string>) {
  const { t } = useI18n()

  const items = ref<MyInventoryItem[]>([])
  const exchangeRequests = ref<ExchangeRequestEntry[]>([])
  const exchangeSizes = ref<InventorySize[]>([])
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
    try {
      const allExch = await exchanges.listExchanges()
      exchangeRequests.value = allExch.filter(e => e.memberId === memberId.value && stillMoving(e.status))
    } catch { exchangeRequests.value = [] }
  }

  async function assignItem(itemId: number) {
    error.value = ''
    try {
      await inventory.assignItem(itemId, { memberId: memberId.value })
      items.value = await inventory.memberItems(memberId.value)
      await loadRequirements()
    } catch { error.value = t('common.error') }
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

  async function submitExchange(data: { item: MyInventoryItem; newSizeId?: number; reason: string }) {
    error.value = ''
    try {
      await exchanges.createExchange({
        memberId: memberId.value,
        itemId: data.item.id,
        inventoryId: data.item.inventoryId,
        oldSizeId: data.item.sizeId ?? undefined,
        newSizeId: data.newSizeId,
        reason: data.reason,
      })
    } catch { error.value = t('common.error') }
  }

  async function loadExchangeSizes(inventoryId: number) {
    try { exchangeSizes.value = await inventory.listSizes(inventoryId) } catch { exchangeSizes.value = [] }
  }

  return {
    items,
    exchangeRequests,
    exchangeSizes,
    requirements,
    load,
    assignItem,
    handOutNewItem,
    unassignItem,
    reassignItem,
    submitExchange,
    loadExchangeSizes,
  }
}

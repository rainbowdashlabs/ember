/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InventorySize, MyInventoryItem } from '@/api/inventory'
import {ExchangeStatus, type ExchangeRequestEntry} from '@/api/exchanges'
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

  async function load() {
    try { items.value = await inventory.memberItems(memberId.value) } catch { items.value = [] }
    try {
      const allExch = await exchanges.listExchanges()
      exchangeRequests.value = allExch.filter(e => e.memberId === memberId.value && e.status !== ExchangeStatus.DONE)
    } catch { exchangeRequests.value = [] }
  }

  async function assignItem(itemId: number) {
    error.value = ''
    try {
      await inventory.assignItem(itemId, { memberId: memberId.value })
      items.value = await inventory.memberItems(memberId.value)
    } catch { error.value = t('common.error') }
  }

  async function unassignItem(item: MyInventoryItem) {
    error.value = ''
    try {
      await inventory.assignItem(item.id, { memberId: null })
      items.value = await inventory.memberItems(memberId.value)
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
    load,
    assignItem,
    unassignItem,
    reassignItem,
    submitExchange,
    loadExchangeSizes,
  }
}

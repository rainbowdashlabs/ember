/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {equipment, inventory as inventoryApi, inventoryArts} from '@/api'
import type {NeedCoverage} from '@/api/equipment'
import type {Inventory, InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

/** How the modal's hours reach the line, which counts in minutes because a lead is not whole days. */
const MINUTES_PER_HOUR = 60

/**
 * What an appointment needs, read for one evening and written for the series or for that evening.
 *
 * <p>The pickers need the station's own gear, which is loaded once: every inventory, every piece in
 * them and every kind of every mixed inventory, so one picker covers the lot.
 */
export function useEquipmentNeeds(eventId: Ref<number>, date: Ref<string | null>) {
  const coverage = ref<NeedCoverage[]>([])
  const inventories = ref<Inventory[]>([])
  const items = ref<InventoryItem[]>([])
  const arts = ref<InventoryArt[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')

  async function loadCoverage() {
    if (!date.value) {
      coverage.value = []
      return
    }
    loading.value = true
    try {
      coverage.value = await equipment.coverage(eventId.value, date.value)
      error.value = ''
    } catch {
      coverage.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadPickers() {
    const [entries, allItems] = await Promise.all([
      inventoryApi.listInventories().catch(() => [] as Inventory[]),
      inventoryApi.listAllItems().catch(() => [] as InventoryItem[]),
    ])
    inventories.value = entries
    items.value = allItems
    arts.value = (
        await Promise.all(entries.filter(entry => !entry.homogeneous).map(entry => inventoryArts.listArts(entry.id)))
    ).flat()
  }

  async function add(payload: {
    kind: 'item' | 'art' | 'inventory'
    itemId: string
    artId: string
    inventoryId: string
    quantity: number
    leadHours: number
    trailHours: number
    thisEveningOnly: boolean
  }) {
    saving.value = true
    try {
      await equipment.add(eventId.value, {
        itemId: payload.kind === 'item' ? Number(payload.itemId) : null,
        artId: payload.kind === 'art' ? Number(payload.artId) : null,
        inventoryId: payload.kind === 'inventory' ? Number(payload.inventoryId) : null,
        quantity: payload.kind === 'item' ? 1 : payload.quantity,
        leadMinutes: payload.leadHours * MINUTES_PER_HOUR,
        trailMinutes: payload.trailHours * MINUTES_PER_HOUR,
        eventDate: payload.thisEveningOnly ? date.value : null,
      })
      error.value = ''
      await loadCoverage()
      return true
    } catch (e) {
      error.value = (e as {response?: {data?: {message?: string}}}).response?.data?.message ?? ''
      return false
    } finally {
      saving.value = false
    }
  }

  async function remove(needId: number) {
    await equipment.remove(eventId.value, needId)
    await loadCoverage()
  }

  return {coverage, inventories, items, arts, loading, saving, error, loadCoverage, loadPickers, add, remove}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {equipment, inventory as inventoryApi, inventoryArts} from '@/api'
import type {NeedCoverage} from '@/api/equipment'
import type {Inventory, InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'
import {apiErrorMessage} from '@/util/apiError'
import {describeFailure, type Failure} from '@/util/failure'

/** How the modal's hours reach the line, which counts in minutes because a lead is not whole days. */
const MINUTES_PER_HOUR = 60

/**
 * What an appointment needs, read for one date and written for the series or for that date.
 *
 * <p>The pickers need the station's own gear, which is loaded once: every inventory, every piece in
 * them and every kind of every mixed inventory, so one picker covers the lot.
 *
 * <p>Reading and writing keep their failures apart: {@code failure} is what the panel could not
 * read, {@code saveFailure} is what a line could not be written as, which is what the dialog shows
 * while it is open. A read that failed says so rather than showing an empty list, because nothing
 * planned and nothing readable are different answers.
 */
export function useEquipmentNeeds(eventId: Ref<number>, date: Ref<string | null>) {
  const {t} = useI18n()
  const coverage = ref<NeedCoverage[]>([])
  const inventories = ref<Inventory[]>([])
  const items = ref<InventoryItem[]>([])
  const arts = ref<InventoryArt[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const failure = ref<Failure | null>(null)
  const saveFailure = ref<Failure | null>(null)

  /**
   * What went wrong, in the terms the reader can act on.
   *
   * <p>Where the server wrote a sentence it wins, because it names the rule that was broken and this
   * screen cannot: not enough of a thing in stock, an art that is not kept in that inventory, a line
   * already handed out. Such a refusal is marked as nothing to report, since the reader ran into the
   * product working as intended and a bug filed against that buries the real ones. Everything else,
   * a server that fell over or a request that never arrived, is described as it stands, report
   * button and all, because then it really is one.
   */
  function refusalFailure(e: unknown): Failure {
    const described = describeFailure(e, t)
    const said = apiErrorMessage(e)
    return said ? {...described, message: said, reportable: false} : described
  }

  async function loadCoverage() {
    if (!date.value) {
      coverage.value = []
      return
    }
    loading.value = true
    try {
      coverage.value = await equipment.coverage(eventId.value, date.value)
      failure.value = null
    } catch (e) {
      coverage.value = []
      failure.value = refusalFailure(e)
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
        await Promise.all(entries
            .filter(entry => !entry.homogeneous)
            .map(entry => inventoryArts.listArts(entry.id).catch(() => [] as InventoryArt[])))
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
    thisDateOnly: boolean
  }) {
    saving.value = true
    saveFailure.value = null
    try {
      await equipment.add(eventId.value, {
        itemId: payload.kind === 'item' ? Number(payload.itemId) : null,
        artId: payload.kind === 'art' ? Number(payload.artId) : null,
        inventoryId: payload.kind === 'inventory' ? Number(payload.inventoryId) : null,
        quantity: payload.kind === 'item' ? 1 : payload.quantity,
        leadMinutes: payload.leadHours * MINUTES_PER_HOUR,
        trailMinutes: payload.trailHours * MINUTES_PER_HOUR,
        eventDate: payload.thisDateOnly ? date.value : null,
      })
      await loadCoverage()
      return true
    } catch (e) {
      saveFailure.value = refusalFailure(e)
      return false
    } finally {
      saving.value = false
    }
  }

  async function remove(needId: number) {
    saveFailure.value = null
    try {
      await equipment.remove(eventId.value, needId)
      await loadCoverage()
    } catch (e) {
      saveFailure.value = refusalFailure(e)
    }
  }

  function clearSaveFailure() {
    saveFailure.value = null
  }

  return {
    coverage, inventories, items, arts, loading, saving, failure, saveFailure,
    loadCoverage, loadPickers, add, remove, clearSaveFailure,
  }
}

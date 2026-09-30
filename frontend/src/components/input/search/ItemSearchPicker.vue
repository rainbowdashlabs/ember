/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import ItemChip from '@/components/inventory/ItemChip.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import type {InventoryItem, InventoryTypeName, ItemOwnerName} from '@/api/inventory'
import {passesItemFilters} from './itemsearch/itemFilters'
import {ITEM_SEARCH_LIMIT, queryTokens, scoreItemText} from './itemsearch/itemRanking'
import {useItemCatalog} from './itemsearch/useItemCatalog'

/**
 * Picking one piece of gear, by searching for it or by scanning its code.
 *
 * <p>The picker holds nothing of its own: what it knows about the gear and how it words a piece come
 * from `useItemCatalog`, which pieces it may offer from `itemFilters`, and the order of a search from
 * `itemRanking`. What stays here is the wiring between them and the search field.
 */
const model = defineModel<number | null>()

const props = defineProps<{
  inventoryId?: number | null
  excludeAssigned?: boolean
  excludeLost?: boolean
  excludeContainerless?: boolean
  /**
   * Whose gear may be offered. A replacement for the station's gear comes off the station's shelf and
   * one for the association's comes out of theirs: offering both invites handing the wrong body's
   * property over, and the movement would be refused for it afterwards anyway.
   */
  ownerKind?: ItemOwnerName | null
  /** Which association that is, so one association's gear is not offered for another's movement. */
  ownerClusterId?: string | null
  /**
   * Which kind of inventory the pieces have to sit in: the station's own gear, the association's, or
   * a shelf that holds both. A mixed shelf answers for either side, because it is what its pieces say
   * it is.
   */
  inventoryType?: InventoryTypeName | null
  /** Whether pieces another open movement has already promised somebody are left out. */
  excludeSpokenFor?: boolean
  /** The size asked for, whose pieces are drawn as a fit and offered first. */
  wantedSizeId?: number | null
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  pick: [item: InventoryItem]
  scanNotFound: [code: string]
}>()

const {t} = useI18n()

const catalog = useItemCatalog(() => props.wantedSizeId, () => props.excludeSpokenFor)
const {items, ready, itemById, searchText, displayName, chipOf, stateBadge, subtitle} = catalog

const scanError = ref('')

function passesFilters(item: InventoryItem): boolean {
  return passesItemFilters(item, props, {
    inventoryTypeOf: id => catalog.inventoryById.value.get(id)?.inventoryType,
    spokenFor: catalog.spokenFor.value,
  })
}

const filtered = computed(() => items.value.filter(passesFilters))

/** Pieces of the size asked for come before the rest, which is the piece a reader is looking for. */
function byWantedSize(a: InventoryItem, b: InventoryItem): number {
  return Number(catalog.hasWantedSize(b)) - Number(catalog.hasWantedSize(a))
}

function byName(a: InventoryItem, b: InventoryItem): number {
  return displayName(a).localeCompare(displayName(b), 'de')
}

async function searchFn(query: string): Promise<InventoryItem[]> {
  if (!ready.value) return []
  const tokens = queryTokens(query)
  if (tokens.length === 0) {
    return filtered.value.slice().sort((a, b) => byWantedSize(a, b) || byName(a, b)).slice(0, ITEM_SEARCH_LIMIT)
  }
  const scored: Array<[InventoryItem, number]> = []
  for (const item of filtered.value) {
    const text = searchText.value.get(item.id)
    const score = text ? scoreItemText(text, tokens) : -1
    if (score >= 0) scored.push([item, score])
  }
  scored.sort(([a, scoreA], [b, scoreB]) => byWantedSize(a, b) || scoreB - scoreA || byName(a, b))
  return scored.slice(0, ITEM_SEARCH_LIMIT).map(([item]) => item)
}

const innerModel = computed<string | null>({
  get: () => model.value != null ? String(model.value) : null,
  set: v => { model.value = v != null && v !== '' ? Number(v) : null },
})

const selectedItem = computed(() => (model.value != null ? itemById.value.get(model.value) ?? null : null))

const selectedDisplay = computed(() => {
  if (model.value == null) return null
  return selectedItem.value ? displayName(selectedItem.value) : `#${model.value}`
})

function pickItem(item: InventoryItem) {
  model.value = item.id
  emit('pick', item)
}

function onScan(value: string) {
  const term = normaliseScannedPayload(value).trim()
  if (!term) return
  scanError.value = ''
  const match = items.value.find(i => (i.internalId ?? '').toLowerCase() === term.toLowerCase())
  if (match && passesFilters(match)) {
    pickItem(match)
    return
  }
  emit('scanNotFound', term)
  if (!match) scanError.value = t('inventory.itemPicker.scanNotFound', {scan: term})
}

onMounted(catalog.load)
watch(() => model.value, val => { if (val == null) scanError.value = '' })
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-stretch gap-2">
      <div class="flex-1">
        <EntitySearchPicker
            v-model="innerModel"
            :search-fn="searchFn"
            :display-fn="displayName"
            :subtitle-fn="subtitle"
            :key-fn="(item: InventoryItem) => item.id"
            :badge-fn="stateBadge"
            :selected-display="selectedDisplay"
            :placeholder="placeholder ?? t('inventory.itemPicker.placeholder')"
            :disabled="disabled"
            :empty-label="t('inventory.itemPicker.emptyNoMatch')"
            @pick="pickItem"
        >
          <template #row="{item, highlighted}">
            <ItemChip :source="chipOf(item)" :surface="highlighted ? 'highlight' : 'page'"/>
          </template>
          <template #picked>
            <ItemChip v-if="selectedItem" :source="chipOf(selectedItem)"/>
            <span v-else class="flex-1 truncate text-sm">{{ selectedDisplay }}</span>
          </template>
        </EntitySearchPicker>
      </div>
      <ScanButton :disabled="disabled" @decoded="onScan" />
    </div>
    <Alert v-if="scanError" variant="error">{{ scanError }}</Alert>
  </div>
</template>

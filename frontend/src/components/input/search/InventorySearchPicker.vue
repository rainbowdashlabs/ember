/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import {listSearch, numericPickerModel} from '@/util/listSearch'
import {glyphFor} from '@/util/glyph'
import {InventoryTypes, type Inventory} from '@/api/inventory'

const model = defineModel<number | null>()

const props = defineProps<{
  inventories: Inventory[]
  /** How many pieces each inventory holds at hand, which is what turns a name into a choice. */
  stock?: Map<number, number>
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  pick: [inventory: Inventory]
}>()

const {t} = useI18n()

const entries = computed(() => props.inventories)
const searchFn = listSearch(entries, entry => entry.name ?? '')
const displayFn = (entry: Inventory) => entry.name ?? String(entry.id)
const keyFn = (entry: Inventory) => entry.id

/**
 * What an inventory is, in the words the badges on the manage page already use: whose gear it holds,
 * and whether it is a stock of one thing or a drawer of different things. Nine inventories of a
 * station differ in exactly those two things and in their picture.
 */
function subtitleFn(entry: Inventory): string {
  const owner = t(`inventory.manage.type.${entry.inventoryType ?? InventoryTypes.INTERNAL}`)
  const kind = entry.homogeneous ? t('inventory.manage.kindStockName') : t('inventory.manage.kindCollectionName')
  return `${owner} · ${kind}`
}

function glyphOf(entry: Inventory) {
  return glyphFor({icon: entry.icon, color: entry.color, homogeneous: entry.homogeneous})
}

function badgeFn(entry: Inventory) {
  if (!props.stock) return null
  const count = props.stock.get(entry.id) ?? 0
  return {text: t('inventory.stock.pieces', {count}), variant: count > 0 ? 'neutral' as const : 'error' as const}
}

const innerModel = numericPickerModel(model)

function pickInventory(entry: Inventory) {
  model.value = entry.id
  emit('pick', entry)
}

const selected = computed(() => {
  if (model.value == null) return null
  return props.inventories.find(candidate => candidate.id === model.value) ?? null
})

const selectedDisplay = computed(() => (selected.value ? displayFn(selected.value) : null))
</script>

<template>
  <EntitySearchPicker
      v-model="innerModel"
      :search-fn="searchFn"
      :display-fn="displayFn"
      :key-fn="keyFn"
      :subtitle-fn="subtitleFn"
      :badge-fn="badgeFn"
      :selected-display="selectedDisplay"
      :placeholder="placeholder ?? t('inventory.inventoryPicker.placeholder')"
      :disabled="disabled"
      :empty-label="t('inventory.inventoryPicker.empty')"
      @pick="pickInventory"
  >
    <template #row="{item, highlighted}">
      <GearGlyph :glyph="glyphOf(item)" :surface="highlighted ? 'highlight' : 'page'"/>
      <span class="flex min-w-0 flex-1 flex-col items-start text-left">
        <span class="truncate font-medium">{{ displayFn(item) }}</span>
        <span class="truncate text-xs text-(--text-muted)">{{ subtitleFn(item) }}</span>
      </span>
    </template>
    <template #picked>
      <GearGlyph v-if="selected" :glyph="glyphOf(selected)"/>
      <span class="flex-1 truncate text-sm">{{ selectedDisplay }}</span>
    </template>
  </EntitySearchPicker>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {listSearch, numericPickerModel} from '@/util/listSearch'
import type {InventoryChoice, ItemChoice} from '@/api/generated/schema'

/**
 * Picking one piece out of a list the caller already holds, searched by its name, its code, its size
 * and its inventory.
 *
 * <p>Where the full item picker reads the whole inventory itself, this one is handed only what a
 * line of an appointment may know about a piece, which is what lets somebody who plans an
 * appointment without reading the inventory name a piece all the same.
 */
const model = defineModel<number | null>()

const props = defineProps<{
  items: ItemChoice[]
  /** The inventories, so a piece can say which one it sits in. */
  inventories: InventoryChoice[]
  placeholder?: string
  disabled?: boolean
}>()

const {t} = useI18n()

const inventoryName = (id: number) => props.inventories.find(entry => entry.id === id)?.name ?? ''

function displayFn(item: ItemChoice): string {
  const name = item.name.trim() || item.internalId || `#${item.id}`
  return item.sizeLabel ? `${name} · ${item.sizeLabel}` : name
}

function subtitleFn(item: ItemChoice): string {
  return [item.internalId, inventoryName(item.inventoryId)].filter(Boolean).join(' · ')
}

const entries = computed(() => props.items)
const searchFn = listSearch(entries, item => `${displayFn(item)} ${subtitleFn(item)}`)
const keyFn = (item: ItemChoice) => item.id
const iconFn = (): string[] => ['fas', 'box']

const innerModel = numericPickerModel(model)

function pickItem(item: ItemChoice) {
  model.value = item.id
}

const selectedDisplay = computed(() => {
  if (model.value == null) return null
  const item = props.items.find(entry => entry.id === model.value)
  return item ? displayFn(item) : null
})
</script>

<template>
  <EntitySearchPicker
      v-model="innerModel"
      :search-fn="searchFn"
      :display-fn="displayFn"
      :subtitle-fn="subtitleFn"
      :key-fn="keyFn"
      :icon-fn="iconFn"
      :selected-display="selectedDisplay"
      :placeholder="placeholder ?? t('inventory.itemPicker.searchPlaceholder')"
      :disabled="disabled"
      :empty-label="t('inventory.itemPicker.emptyNoMatch')"
      @pick="pickItem"
  />
</template>

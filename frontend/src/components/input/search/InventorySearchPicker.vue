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
import type {Inventory} from '@/api/inventory'

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
const iconFn = (): string[] => ['fas', 'warehouse']

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

const selectedDisplay = computed(() => {
  if (model.value == null) return null
  const entry = props.inventories.find(candidate => candidate.id === model.value)
  return entry ? displayFn(entry) : null
})
</script>

<template>
  <EntitySearchPicker
      v-model="innerModel"
      :search-fn="searchFn"
      :display-fn="displayFn"
      :key-fn="keyFn"
      :icon-fn="iconFn"
      :badge-fn="badgeFn"
      :selected-display="selectedDisplay"
      :placeholder="placeholder ?? t('inventory.inventoryPicker.placeholder')"
      :disabled="disabled"
      :empty-label="t('inventory.inventoryPicker.empty')"
      @pick="pickInventory"
  />
</template>

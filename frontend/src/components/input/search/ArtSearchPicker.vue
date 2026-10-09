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
import type {ArtChoice, InventoryChoice} from '@/api/generated/schema'

const model = defineModel<number | null>()

const props = defineProps<{
  /** Every kind of every mixed inventory, so one picker covers them all. */
  arts: ArtChoice[]
  /** The inventories, so a kind can say which drawer it belongs to. */
  inventories: InventoryChoice[]
  /** How many pieces of each kind are at hand, which is what turns a name into a choice. */
  stock?: Map<number, number>
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  pick: [art: ArtChoice]
}>()

const {t} = useI18n()

const inventoryName = (id: number) => props.inventories.find(entry => entry.id === id)?.name ?? ''

const entries = computed(() => props.arts)
const searchFn = listSearch(entries, art => `${art.name} ${inventoryName(art.inventoryId)}`)
const displayFn = (art: ArtChoice) => art.name
const subtitleFn = (art: ArtChoice) => inventoryName(art.inventoryId)
const keyFn = (art: ArtChoice) => art.id
const iconFn = (): string[] => ['fas', 'layer-group']

function badgeFn(art: ArtChoice) {
  if (!props.stock) return null
  const count = props.stock.get(art.id) ?? 0
  return {text: t('inventory.stock.pieces', {count}), variant: count > 0 ? 'neutral' as const : 'error' as const}
}

const innerModel = numericPickerModel(model)

function pickArt(art: ArtChoice) {
  model.value = art.id
  emit('pick', art)
}

const selectedDisplay = computed(() => {
  if (model.value == null) return null
  const art = props.arts.find(entry => entry.id === model.value)
  return art ? `${art.name} (${inventoryName(art.inventoryId)})` : null
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
      :badge-fn="badgeFn"
      :selected-display="selectedDisplay"
      :placeholder="placeholder ?? t('inventory.artPicker.placeholder')"
      :disabled="disabled"
      :empty-label="t('inventory.artPicker.empty')"
      @pick="pickArt"
  />
</template>

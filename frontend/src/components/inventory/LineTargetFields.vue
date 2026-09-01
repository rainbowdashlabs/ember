/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import ArtSearchPicker from '@/components/input/search/ArtSearchPicker.vue'
import InventorySearchPicker from '@/components/input/search/InventorySearchPicker.vue'
import StockHint from './StockHint.vue'
import {stockByArt, stockByInventory} from '@/util/inventoryStock'
import type {Inventory, InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

const kind = defineModel<'item' | 'art' | 'inventory'>('kind', {required: true})
const itemId = defineModel<string>('itemId', {required: true})
const artId = defineModel<string>('artId', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const quantity = defineModel<number>('quantity', {required: true})

const props = defineProps<{
  inventories: Inventory[]
  items: InventoryItem[]
  arts: InventoryArt[]
}>()

const {t} = useI18n()

/**
 * Only the inventories that hold one thing in many copies are worth counting whole. A drawer of
 * different things is counted by its kinds, which is what the kind picker is for.
 */
const countable = computed(() => props.inventories.filter(inventory => inventory.homogeneous))

const artStock = computed(() => stockByArt(props.items))
const inventoryStock = computed(() => stockByInventory(props.items))

/**
 * A picker speaks in numbers and the surrounding form in strings, because a form field that has not
 * been filled in is an empty string rather than a number nobody wrote.
 */
function numberModel(model: Ref<string>) {
  return computed<number | null>({
    get: () => (model.value ? Number(model.value) : null),
    set: value => {
      model.value = value == null ? '' : String(value)
    },
  })
}

const pickedItem = numberModel(itemId)
const pickedArt = numberModel(artId)
const pickedInventory = numberModel(inventoryId)

const chosenArtStock = computed(() => (pickedArt.value == null ? null : artStock.value.get(pickedArt.value) ?? 0))
const chosenInventoryStock = computed(() =>
    pickedInventory.value == null ? null : inventoryStock.value.get(pickedInventory.value) ?? 0)
</script>

<template>
  <div class="space-y-4">
    <template v-if="kind === 'item'">
      <FieldLabel>{{ t('inventory.collections.item') }}</FieldLabel>
      <ItemSearchPicker v-model="pickedItem" data-testid="line-target-item"/>
    </template>

    <template v-else-if="kind === 'art'">
      <FieldLabel>{{ t('inventory.collections.art') }}</FieldLabel>
      <ArtSearchPicker
          v-model="pickedArt"
          :arts="arts"
          :inventories="inventories"
          :stock="artStock"
          data-testid="line-target-art"
      />
      <FieldHint v-if="arts.length === 0">{{ t('inventory.collections.noArts') }}</FieldHint>
      <FieldLabel>{{ t('inventory.collections.quantity') }}</FieldLabel>
      <NumberInput v-model="quantity" :min="1" data-testid="line-target-art-quantity"/>
      <StockHint v-if="chosenArtStock !== null" :stock="chosenArtStock" :quantity="quantity"/>
    </template>

    <template v-else>
      <FieldLabel>{{ t('inventory.collections.inventory') }}</FieldLabel>
      <InventorySearchPicker
          v-model="pickedInventory"
          :inventories="countable"
          :stock="inventoryStock"
          data-testid="line-target-inventory"
      />
      <FieldLabel>{{ t('inventory.collections.quantity') }}</FieldLabel>
      <NumberInput v-model="quantity" :min="1" data-testid="line-target-quantity"/>
      <StockHint v-if="chosenInventoryStock !== null" :stock="chosenInventoryStock" :quantity="quantity"/>
    </template>
  </div>
</template>

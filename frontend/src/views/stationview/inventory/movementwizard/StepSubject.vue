/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import InventorySearchPicker from '@/components/input/search/InventorySearchPicker.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import type {Inventory, InventoryItem, InventorySize} from '@/api/inventory'

/**
 * What the Vorgang is about.
 *
 * <p>A return or a swap starts from a piece somebody holds, so it asks which piece and reads the
 * inventory off it. An issue or a request is about a thing the station does not hold yet, so it asks for
 * an inventory and a size instead. Both questions are asked with the pickers every other screen uses.
 */
const props = defineProps<{
  /** Whether a piece is handed in, which is what decides the question. */
  fromAPiece: boolean
  inventories: Inventory[]
  sizes: InventorySize[]
}>()

const itemId = defineModel<number | null>('itemId', {required: true})
const inventoryId = defineModel<number | null>('inventoryId', {required: true})
const oldSizeId = defineModel<number | null>('oldSizeId', {required: true})
const newSizeId = defineModel<number | null>('newSizeId', {required: true})

const {t} = useI18n()

const sizeChoice = computed({
  get: () => (newSizeId.value != null ? String(newSizeId.value) : ''),
  set: value => {
    newSizeId.value = value ? Number(value) : null
  },
})

/** A piece answers for its own inventory and its own size, so neither is asked twice. */
function tookPiece(item: InventoryItem) {
  inventoryId.value = item.inventoryId
  oldSizeId.value = item.sizeId ?? null
}

watch(inventoryId, () => {
  newSizeId.value = null
})
</script>

<template>
  <div class="space-y-3">
    <SubHeader>
      {{ props.fromAPiece ? t('movements.wizard.subject.titleItem') : t('movements.wizard.subject.titleInventory') }}
    </SubHeader>

    <div v-if="props.fromAPiece" class="space-y-1">
      <FieldLabel>{{ t('movements.wizard.subject.item') }}</FieldLabel>
      <ItemSearchPicker v-model="itemId" data-testid="wizard-item" exclude-lost @pick="tookPiece"/>
    </div>

    <template v-else>
      <div class="space-y-1">
        <FieldLabel>{{ t('movements.wizard.subject.inventory') }}</FieldLabel>
        <InventorySearchPicker v-model="inventoryId" :inventories="props.inventories" data-testid="wizard-inventory"/>
      </div>
      <div v-if="props.sizes.length > 0" class="space-y-1">
        <FieldLabel>{{ t('movements.wizard.subject.size') }}</FieldLabel>
        <SelectInput v-model="sizeChoice" data-testid="wizard-size">
          <option value="">{{ t('movements.wizard.subject.anySize') }}</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
    </template>
  </div>
</template>

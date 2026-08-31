/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
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

const inventoryName = (id: number) => props.inventories.find(inventory => inventory.id === id)?.name ?? ''

/**
 * Only the inventories that hold one thing in many copies are worth counting whole. A drawer of
 * different things is counted by its kinds, which is what the kind picker is for.
 */
const countable = computed(() => props.inventories.filter(inventory => inventory.homogeneous))
</script>

<template>
  <div class="space-y-4">
    <template v-if="kind === 'item'">
      <FieldLabel>{{ t('inventory.collections.item') }}</FieldLabel>
      <SelectInput v-model="itemId" data-testid="collection-line-item">
        <option value="">{{ t('inventory.collections.selectItem') }}</option>
        <option v-for="item in items" :key="item.id" :value="String(item.id)">
          {{ item.name }} ({{ inventoryName(item.inventoryId) }})
        </option>
      </SelectInput>
    </template>

    <template v-else-if="kind === 'art'">
      <FieldLabel>{{ t('inventory.collections.art') }}</FieldLabel>
      <SelectInput v-model="artId" data-testid="collection-line-art">
        <option value="">{{ t('inventory.collections.selectArt') }}</option>
        <option v-for="art in arts" :key="art.id" :value="String(art.id)">
          {{ art.name }} ({{ inventoryName(art.inventoryId) }})
        </option>
      </SelectInput>
      <FieldHint v-if="arts.length === 0">{{ t('inventory.collections.noArts') }}</FieldHint>
      <FieldLabel>{{ t('inventory.collections.quantity') }}</FieldLabel>
      <NumberInput v-model="quantity" :min="1" data-testid="collection-line-art-quantity"/>
    </template>

    <template v-else>
      <FieldLabel>{{ t('inventory.collections.inventory') }}</FieldLabel>
      <SelectInput v-model="inventoryId" data-testid="collection-line-inventory">
        <option value="">{{ t('inventory.collections.selectInventory') }}</option>
        <option v-for="inv in countable" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
      </SelectInput>
      <FieldLabel>{{ t('inventory.collections.quantity') }}</FieldLabel>
      <NumberInput v-model="quantity" :min="1" data-testid="collection-line-quantity"/>
    </template>
  </div>
</template>

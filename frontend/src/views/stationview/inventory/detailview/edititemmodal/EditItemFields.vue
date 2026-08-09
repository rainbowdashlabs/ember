/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import type {InventorySize} from '@/api/inventory'
import type {InventoryContainer} from '@/api/inventoryContainers'

defineProps<{
  hasSizes: boolean
  sizes: InventorySize[]
  containers: InventoryContainer[]
}>()

const itemName = defineModel<string>('itemName', {default: ''})
const internalId = defineModel<string>('internalId', {default: ''})
const sizeId = defineModel<string>('sizeId', {default: ''})
const containerId = defineModel<number | null>('containerId', {default: null})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemName') }}</FieldLabel>
      <TextInput v-model="itemName" :placeholder="t('inventory.edit.itemNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemInternalId') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <TextInput v-model="internalId" class="flex-1" :placeholder="t('inventory.edit.itemInternalIdPlaceholder')"/>
        <ScanButton @decoded="internalId = $event"/>
      </div>
    </div>
    <div v-if="hasSizes" class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
      <SelectInput v-model="sizeId">
        <option value="">&#x2013;</option>
        <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemLocation') }}</FieldLabel>
      <SelectInput v-model="containerId">
        <option :value="null">{{ t('inventory.edit.itemLocationNone') }}</option>
        <option v-for="c in containers" :key="c.id" :value="c.id">{{ c.name }}</option>
      </SelectInput>
    </div>
  </div>
</template>

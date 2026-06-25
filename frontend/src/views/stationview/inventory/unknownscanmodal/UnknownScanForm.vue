/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import CustomFieldsSection from '@/views/stationview/inventory/detailview/CustomFieldsSection.vue'
import NewInventoryFields from '@/views/stationview/inventory/unknownscanmodal/NewInventoryFields.vue'
import ItemBasicsFields from '@/views/stationview/inventory/unknownscanmodal/ItemBasicsFields.vue'
import type {Inventory} from '@/api/types'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

const targetInventoryId = defineModel<number | 'new'>('targetInventoryId', {required: true})
const newInventoryName = defineModel<string>('newInventoryName', {required: true})
const newInventoryType = defineModel<'INTERNAL' | 'EXTERNAL' | 'MIXED'>('newInventoryType', {required: true})
const newInventoryHasSizes = defineModel<boolean>('newInventoryHasSizes', {required: true})
const newInventorySizes = defineModel<string[]>('newInventorySizes', {required: true})
const itemName = defineModel<string>('itemName', {required: true})
const pickedSizeLabel = defineModel<string>('pickedSizeLabel', {required: true})
const itemSource = defineModel<'INTERNAL' | 'EXTERNAL'>('itemSource', {required: true})
const fieldValues = defineModel<Record<string, unknown>>('fieldValues', {required: true})

defineProps<{
  sortedInventories: Inventory[]
  isCreatingInventory: boolean
  effectiveHasSizes: boolean
  sizeOptionLabels: string[]
  showSourcePicker: boolean
  fieldDefs: InventoryFieldDefinition[]
}>()

const emit = defineEmits<{
  addNewSize: []
  removeNewSize: [index: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.unknownScan.targetInventory') }}</FieldLabel>
      <SelectInput
          :model-value="String(targetInventoryId)"
          @update:model-value="(v: string) => targetInventoryId = v === 'new' ? 'new' : Number(v)"
      >
        <option v-for="inv in sortedInventories" :key="inv.id" :value="inv.id">{{ inv.name }}</option>
        <option value="new">{{ t('inventory.unknownScan.createNewInventory') }}</option>
      </SelectInput>
    </div>

    <NewInventoryFields
        v-if="isCreatingInventory"
        v-model:name="newInventoryName"
        v-model:type="newInventoryType"
        v-model:has-sizes="newInventoryHasSizes"
        v-model:sizes="newInventorySizes"
        @add-size="emit('addNewSize')"
        @remove-size="(idx) => emit('removeNewSize', idx)"
    />

    <ItemBasicsFields
        v-model:item-name="itemName"
        v-model:picked-size="pickedSizeLabel"
        v-model:item-source="itemSource"
        :show-size-picker="effectiveHasSizes"
        :size-labels="sizeOptionLabels"
        :show-source-picker="showSourcePicker"
    />

    <template v-if="fieldDefs.length > 0">
      <SubHeader class="pt-2">{{ t('inventory.fields.title') }}</SubHeader>
      <CustomFieldsSection
          v-model="fieldValues"
          :defs="fieldDefs"
      />
    </template>
  </div>
</template>

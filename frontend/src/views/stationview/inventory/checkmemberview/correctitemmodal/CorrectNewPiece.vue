/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldValueInput from '@/views/stationview/inventory/detailview/FieldValueInput.vue'
import {ItemOwner, type ItemOwnerName} from '@/api/inventory'
import type {RequiredInventoryItem} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

/**
 * The piece the member actually holds, written down for the first time.
 *
 * <p>The same columns the inventory keeps anywhere else, so that a piece born out of a check is as
 * well described as one entered at the desk. Who owns it is only asked where the inventory holds
 * both owners; anywhere else the inventory is the answer.
 */
defineProps<{
  req: RequiredInventoryItem
  fields: InventoryFieldDefinition[]
  asksOwner: boolean
}>()

const owner = defineModel<ItemOwnerName>('owner', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})
const internalId = defineModel<string>('internalId', {required: true})
const fieldValues = defineModel<Record<string, unknown>>('fieldValues', {required: true})

const {t} = useI18n()

function setField(key: string, value: unknown) {
  fieldValues.value = {...fieldValues.value, [key]: value}
}
</script>

<template>
  <div class="space-y-3">
    <div v-if="asksOwner" class="space-y-1">
      <FieldLabel>{{ t('inventory.check.correct.owner') }}</FieldLabel>
      <SelectInput v-model="owner" class="w-full" data-testid="correct-owner">
        <option :value="ItemOwner.STATION">{{ t('inventory.check.correct.ownerStation') }}</option>
        <option :value="ItemOwner.CLUSTER">{{ t('inventory.check.correct.ownerCluster') }}</option>
      </SelectInput>
    </div>
    <div v-if="req.hasSizes && req.sizes.length > 0" class="space-y-1">
      <FieldLabel>{{ t('inventory.check.selectSize') }}</FieldLabel>
      <SelectInput v-model="sizeId" class="w-full" data-testid="correct-size">
        <option value="">{{ t('common.unisize') }}</option>
        <option v-for="size in req.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.check.correct.number') }}</FieldLabel>
      <TextInput
          v-model="internalId"
          class="w-full"
          :placeholder="t('inventory.check.correct.numberPlaceholder')"
          data-testid="correct-number"
      />
    </div>
    <div v-for="field in fields" :key="field.id" class="space-y-1">
      <FieldLabel>{{ field.label }}</FieldLabel>
      <FieldValueInput
          :field="field"
          :model-value="fieldValues[field.key]"
          @update:model-value="value => setField(field.key, value)"
      />
    </div>
  </div>
</template>

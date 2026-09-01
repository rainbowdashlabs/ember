/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import LineTargetFields from '@/components/inventory/LineTargetFields.vue'
import type {Inventory, InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

const show = defineModel<boolean>('show', {required: true})
const kind = defineModel<'item' | 'art' | 'inventory'>('kind', {required: true})
const itemId = defineModel<string>('itemId', {required: true})
const artId = defineModel<string>('artId', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const quantity = defineModel<number>('quantity', {required: true})
const leadHours = defineModel<number>('leadHours', {required: true})
const trailHours = defineModel<number>('trailHours', {required: true})
const thisEveningOnly = defineModel<boolean>('thisEveningOnly', {required: true})

defineProps<{
  inventories: Inventory[]
  items: InventoryItem[]
  arts: InventoryArt[]
  /** Whether the appointment repeats at all, which is what makes an evening of its own possible. */
  recurring: boolean
  saving: boolean
  /** Why the last attempt did not take, shown here because this is where the reader is looking. */
  error: string
}>()

const emit = defineEmits<{
  submit: []
}>()

const {t} = useI18n()

const incomplete = computed(() => {
  if (kind.value === 'item') return !itemId.value
  if (quantity.value < 1) return true
  return kind.value === 'art' ? !artId.value : !inventoryId.value
})
</script>

<template>
  <Modal v-model="show" size="md">
    <div class="space-y-4">
      <SubHeader>{{ t('eventEquipment.addLine') }}</SubHeader>

      <Alert v-if="error" variant="error" data-testid="equipment-line-error">{{ error }}</Alert>

      <FieldLabel>{{ t('inventory.collections.lineKind') }}</FieldLabel>
      <SelectInput v-model="kind" data-testid="equipment-line-kind">
        <option value="item">{{ t('inventory.collections.kindItem') }}</option>
        <option value="art">{{ t('inventory.collections.kindArt') }}</option>
        <option value="inventory">{{ t('inventory.collections.kindCount') }}</option>
      </SelectInput>

      <LineTargetFields
          v-model:kind="kind"
          v-model:item-id="itemId"
          v-model:art-id="artId"
          v-model:inventory-id="inventoryId"
          v-model:quantity="quantity"
          :inventories="inventories"
          :items="items"
          :arts="arts"
      />

      <FieldLabel>{{ t('eventEquipment.lead') }}</FieldLabel>
      <NumberInput v-model="leadHours" :min="0" data-testid="equipment-line-lead"/>
      <FieldHint>{{ t('eventEquipment.leadHint') }}</FieldHint>

      <FieldLabel>{{ t('eventEquipment.trail') }}</FieldLabel>
      <NumberInput v-model="trailHours" :min="0" data-testid="equipment-line-trail"/>

      <label v-if="recurring" class="flex items-center gap-2 text-sm">
        <CheckboxInput v-model="thisEveningOnly" data-testid="equipment-line-once"/>
        <span>{{ t('eventEquipment.thisEveningOnlyLabel') }}</span>
      </label>
      <FieldHint v-if="recurring">{{ t('eventEquipment.thisEveningOnlyHint') }}</FieldHint>

      <div class="flex justify-end gap-2">
        <SecondaryButton data-cancel @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || incomplete" data-testid="equipment-line-submit" @click="emit('submit')">
          {{ t('eventEquipment.addLine') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>

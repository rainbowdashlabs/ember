/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import CollectionLineTargetFields from './CollectionLineTargetFields.vue'
import type {Inventory, InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

const show = defineModel<boolean>('show', {required: true})
const kind = defineModel<'item' | 'art' | 'inventory'>('kind', {required: true})
const itemId = defineModel<string>('itemId', {required: true})
const artId = defineModel<string>('artId', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const quantity = defineModel<number>('quantity', {required: true})

defineProps<{
  inventories: Inventory[]
  items: InventoryItem[]
  /** Every kind of every mixed inventory the station keeps, so one picker covers them all. */
  arts: InventoryArt[]
  saving: boolean
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
      <SubHeader>{{ t('inventory.collections.addLine') }}</SubHeader>

      <FieldLabel>{{ t('inventory.collections.lineKind') }}</FieldLabel>
      <SelectInput v-model="kind" data-testid="collection-line-kind">
        <option value="item">{{ t('inventory.collections.kindItem') }}</option>
        <option value="art">{{ t('inventory.collections.kindArt') }}</option>
        <option value="inventory">{{ t('inventory.collections.kindCount') }}</option>
      </SelectInput>
      <FieldHint>{{ t('inventory.collections.kindHint') }}</FieldHint>

      <CollectionLineTargetFields
          v-model:kind="kind"
          v-model:item-id="itemId"
          v-model:art-id="artId"
          v-model:inventory-id="inventoryId"
          v-model:quantity="quantity"
          :inventories="inventories"
          :items="items"
          :arts="arts"
      />

      <div class="flex justify-end gap-2">
        <SecondaryButton data-cancel @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || incomplete" data-testid="collection-line-submit" @click="emit('submit')">
          {{ t('inventory.collections.addLine') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>

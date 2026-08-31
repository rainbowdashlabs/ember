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
import NumberInput from '@/components/input/number/NumberInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {Inventory, InventoryItem} from '@/api/inventory'

const show = defineModel<boolean>('show', {required: true})
const kind = defineModel<'item' | 'inventory'>('kind', {required: true})
const itemId = defineModel<string>('itemId', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const quantity = defineModel<number>('quantity', {required: true})

const props = defineProps<{
  inventories: Inventory[]
  items: InventoryItem[]
  saving: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const {t} = useI18n()

const inventoryName = (id: number) => props.inventories.find(inv => inv.id === id)?.name ?? ''

const incomplete = computed(() =>
    kind.value === 'item' ? !itemId.value : !inventoryId.value || quantity.value < 1)
</script>

<template>
  <Modal v-model="show" size="md">
    <div class="space-y-4">
      <SubHeader>{{ t('inventory.collections.addLine') }}</SubHeader>

      <FieldLabel>{{ t('inventory.collections.lineKind') }}</FieldLabel>
      <SelectInput v-model="kind" data-testid="collection-line-kind">
        <option value="item">{{ t('inventory.collections.kindItem') }}</option>
        <option value="inventory">{{ t('inventory.collections.kindCount') }}</option>
      </SelectInput>
      <FieldHint>{{ t('inventory.collections.kindHint') }}</FieldHint>

      <template v-if="kind === 'item'">
        <FieldLabel>{{ t('inventory.collections.item') }}</FieldLabel>
        <SelectInput v-model="itemId" data-testid="collection-line-item">
          <option value="">{{ t('inventory.collections.selectItem') }}</option>
          <option v-for="item in items" :key="item.id" :value="String(item.id)">
            {{ item.name }} ({{ inventoryName(item.inventoryId) }})
          </option>
        </SelectInput>
      </template>

      <template v-else>
        <FieldLabel>{{ t('inventory.collections.inventory') }}</FieldLabel>
        <SelectInput v-model="inventoryId" data-testid="collection-line-inventory">
          <option value="">{{ t('inventory.collections.selectInventory') }}</option>
          <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
        </SelectInput>
        <FieldLabel>{{ t('inventory.collections.quantity') }}</FieldLabel>
        <NumberInput v-model="quantity" :min="1" data-testid="collection-line-quantity"/>
      </template>

      <div class="flex justify-end gap-2">
        <SecondaryButton data-cancel @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || incomplete" data-testid="collection-line-submit" @click="emit('submit')">
          {{ t('inventory.collections.addLine') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {InventoryType, ItemOwner} from '@/api/generated/schema'

/**
 * Whose piece an order brings.
 *
 * <p>The inventory's type settles it for an internal or an external inventory, so those only show the
 * answer. A mixed inventory holds both, and there the order has to be given its owner, with no answer
 * picked in advance: an order of the association's piece is how a station notes down what it asked an
 * association outside Ember for, and a guess would record the wrong one.
 */
const owner = defineModel<ItemOwner | ''>({required: true})

defineProps<{
  /** The type of the chosen inventory, or null while none is chosen. */
  inventoryType: InventoryType | null
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="inventoryType === InventoryType.MIXED" class="space-y-1">
    <FieldLabel>{{ t('procurement.owner') }}</FieldLabel>
    <SelectInput v-model="owner" data-testid="procurement-owner">
      <option value="" disabled>{{ t('procurement.chooseOwner') }}</option>
      <option :value="ItemOwner.STATION">{{ t('procurement.ownerStation') }}</option>
      <option :value="ItemOwner.CLUSTER">{{ t('procurement.ownerAssociation') }}</option>
    </SelectInput>
  </div>
  <MutedText v-else-if="inventoryType === InventoryType.EXTERNAL" tag="p" size="sm"
             data-testid="procurement-owner-settled">
    {{ t('procurement.ownerSettledAssociation') }}
  </MutedText>
  <MutedText v-else-if="inventoryType === InventoryType.INTERNAL" tag="p" size="sm"
             data-testid="procurement-owner-settled">
    {{ t('procurement.ownerSettledStation') }}
  </MutedText>
</template>

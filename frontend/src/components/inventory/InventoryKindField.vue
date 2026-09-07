/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {InventoryKinds, inventoryKindOf, isStock} from '@/api/inventory'

/**
 * The choice between the two kinds of inventory, named on both sides.
 *
 * <p>It was a tick box called "one thing in many copies", which left the other kind with no name at
 * all and nothing to recognise it by. Both kinds are now written out with the example that tells
 * them apart, and the hint below follows whichever is chosen.
 *
 * <p>The wire carries a boolean and this field speaks in names, which is the one translation
 * between them. Create and edit both mount this rather than each writing the choice out again.
 */
const homogeneous = defineModel<boolean>({required: true})

const {t} = useI18n()

const kind = computed({
  get: () => inventoryKindOf(homogeneous.value),
  set: value => {
    homogeneous.value = isStock(value)
  },
})
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.manage.kindLabel') }}</FieldLabel>
    <SelectInput v-model="kind" class="w-full" data-testid="inventory-kind">
      <option :value="InventoryKinds.STOCK">{{ t('inventory.manage.kindStockOption') }}</option>
      <option :value="InventoryKinds.COLLECTION">{{ t('inventory.manage.kindCollectionOption') }}</option>
    </SelectInput>
    <p class="text-xs text-(--text-muted)">
      {{ homogeneous ? t('inventory.manage.kindStockHint') : t('inventory.manage.kindCollectionHint') }}
    </p>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {InventoryItem, RequiredInventoryItem} from '@/api/inventory'
import type {CheckResult} from '@/api/inventoryCheck'

/** Everything a check can do about one piece in somebody's hands, in one row of buttons. */
const props = defineProps<{
  item: InventoryItem
  req: RequiredInventoryItem
  result?: CheckResult
}>()

const emit = defineEmits<{
  setResult: [itemId: number, result: CheckResult]
  unassign: [itemId: number]
  correct: [item: InventoryItem, req: RequiredInventoryItem]
}>()

const {t} = useI18n()

const size = 'text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none'
</script>

<template>
  <div class="flex gap-1 shrink-0">
    <SuccessButton
        :class="[size, {'opacity-40': props.result && props.result !== 'CONFIRMED'}]"
        @click="emit('setResult', item.id, 'CONFIRMED')"
    >
      <font-awesome-icon :icon="['fas', 'check']"/>
    </SuccessButton>
    <ErrorButton
        :class="[size, {'opacity-40': props.result && props.result !== 'LOST'}]"
        @click="emit('setResult', item.id, 'LOST')"
    >
      <font-awesome-icon :icon="['fas', 'xmark']"/>
    </ErrorButton>
    <SecondaryButton :class="size" @click="emit('unassign', item.id)">
      <font-awesome-icon :icon="['fas', 'right-from-bracket']"/>
    </SecondaryButton>
    <SecondaryButton :class="size" :data-testid="`correct-item-${item.id}`" @click="emit('correct', item, req)">
      <font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>
      {{ t('inventory.check.correct.action') }}
    </SecondaryButton>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import QuantityStepper from '@/components/input/number/QuantityStepper.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {CartLineCheck, CartLineView} from '@/api/generated/schema'
import {offerLook} from '@/util/offerLook'

/** One line of the cart: what it is, how many, whether the lending station still has it free. */
const props = defineProps<{
  line: CartLineView
  check?: CartLineCheck
  busy?: boolean
}>()

const emit = defineEmits<{
  quantity: [quantity: number]
  remove: []
}>()

const {t} = useI18n()

/** The most of one thing a cart line asks for, as the server keeps it. */
const MAX_QUANTITY = 999

const look = computed(() => offerLook({
  stationId: props.line.stationId,
  pictureKey: props.line.pictureKey,
  icon: null,
  color: null,
  homogeneous: props.line.itemId == null,
}, 128))
const name = computed(() => props.line.lineName || props.line.inventoryName)

const problem = computed(() => {
  const check = props.check
  if (!check || check.state === 'FREE') return ''
  if (check.state === 'SHORT') return t('lendingCartPage.onlyFree', {free: check.free, quantity: props.line.quantity})
  if (check.state === 'GONE') return t('lendingCartPage.gone')
  return t('lendingCartPage.unreachable')
})
</script>

<template>
  <li class="flex flex-wrap items-center gap-3 p-3" :class="problem ? 'bg-error/5' : ''" data-testid="lending-cart-line">
    <PieceThumb v-bind="look"/>
    <div class="min-w-0 flex-1">
      <p class="font-medium">{{ name }}</p>
      <MutedText v-if="line.lineName" tag="p">{{ line.inventoryName }}</MutedText>
      <p v-if="problem" class="text-sm text-error" data-testid="lending-cart-line-problem">{{ problem }}</p>
    </div>
    <QuantityStepper v-if="line.itemId == null" :model-value="line.quantity" :max="MAX_QUANTITY" :disabled="busy"
                     :label="t('lendingCart.quantityOf', {name})" @update:model-value="emit('quantity', $event)"/>
    <MutedText v-else>{{ t('lendingCartPage.singlePiece') }}</MutedText>
    <IconButton :icon="['fas', 'trash']" class="text-error hover:bg-error/15" :disabled="busy"
                :label="t('lendingCartPage.remove', {name})" @click="emit('remove')"/>
  </li>
</template>

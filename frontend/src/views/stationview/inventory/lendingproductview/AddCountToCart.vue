/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import QuantityStepper from '@/components/input/number/QuantityStepper.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {RemoteKind, RemoteProduct} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useLendingCart} from '@/composables/useLendingCart'

/**
 * Puts a count of one row of a product in the cart: up to what is free, and adding to what the
 * cart already holds of that row.
 */
const props = defineProps<{
  stationId: string
  product: RemoteProduct
  kind: RemoteKind
}>()

const {t} = useI18n()
const cart = useLendingCart()
const quantity = ref(1)

const inCart = computed(() => cart.shareOf(props.stationId, props.product.inventoryId)?.lines
    .filter(line => line.itemId == null && line.artId === props.kind.artId)
    .reduce((sum, line) => sum + line.quantity, 0) ?? 0)
/** What can still go in the cart: what is free, less what the cart already holds of it. */
const left = computed(() => Math.max(0, props.kind.free - inCart.value))
watch(left, value => { quantity.value = Math.min(Math.max(1, quantity.value), Math.max(1, value)) })
const name = computed(() => props.kind.name || props.product.name)

const {run, running, failure} = useAsyncAction(() => cart.add({
  lendingStationUid: props.stationId,
  inventoryId: props.product.inventoryId,
  artId: props.kind.artId,
  itemId: null,
  quantity: quantity.value,
  inventoryName: props.product.name,
  lineName: props.kind.name,
  pictureKey: props.kind.pictureKey ?? props.product.pictureKey,
}))
</script>

<template>
  <div class="flex flex-col items-end gap-1">
    <div class="flex items-center gap-2">
      <QuantityStepper v-model="quantity" :max="Math.max(1, left)" :disabled="left < 1"
                       :label="t('lendingCart.quantityOf', {name})"/>
      <PrimaryButton :icon="['fas', 'cart-shopping']" :disabled="left < 1 || running" @click="run()">
        {{ t('lendingCart.add') }}
      </PrimaryButton>
    </div>
    <MutedText v-if="inCart > 0" data-testid="lending-kind-in-cart">{{ t('lendingCart.inCart', {count: inCart}) }}</MutedText>
    <FailureAlert :failure="failure"/>
  </div>
</template>

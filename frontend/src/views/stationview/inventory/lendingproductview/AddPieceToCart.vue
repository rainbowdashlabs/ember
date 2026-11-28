/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {RemotePiece, RemoteProduct} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useLendingCart} from '@/composables/useLendingCart'

/** Puts one single piece in the cart, once; a piece already in it shows so instead. */
const props = defineProps<{
  stationId: string
  product: RemoteProduct
  piece: RemotePiece
}>()

const {t} = useI18n()
const cart = useLendingCart()

const inCart = computed(() => cart.shareOf(props.stationId, props.product.inventoryId)?.lines
    .some(line => line.itemId === props.piece.itemId) ?? false)

const {run, running, failure} = useAsyncAction(() => cart.add({
  lendingStationUid: props.stationId,
  inventoryId: props.product.inventoryId,
  artId: null,
  itemId: props.piece.itemId,
  quantity: 1,
  inventoryName: props.product.name,
  lineName: props.piece.name,
  pictureKey: props.piece.pictureKey,
}))
</script>

<template>
  <div class="flex flex-col items-end gap-1">
    <SuccessBadge v-if="inCart" data-testid="lending-piece-in-cart">{{ t('lendingCart.pieceInCart') }}</SuccessBadge>
    <SecondaryButton v-else :icon="['fas', 'cart-shopping']" :disabled="!piece.free || running"
                     :aria-label="t('lendingCart.addPiece', {name: piece.name})" @click="run()">
      {{ t('lendingCart.add') }}
    </SecondaryButton>
    <FailureAlert :failure="failure"/>
  </div>
</template>

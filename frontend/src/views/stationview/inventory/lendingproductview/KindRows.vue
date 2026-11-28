/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import type {RemoteKind} from '@/api/generated/schema'
import {offerLook} from '@/util/offerLook'
import ProductDescription from './ProductDescription.vue'

/**
 * The count rows of a product: each kind on offer, or the one row of an inventory of copies, with what
 * is free of it. The slot after each row takes what is done with it, such as putting some in the cart.
 */
defineProps<{
  kinds: RemoteKind[]
  stationUid: string
  /** The name of the one row of an inventory of copies, which has no kind of its own. */
  productName: string
}>()

defineSlots<{
  action(props: {kind: RemoteKind}): unknown
}>()

const {t} = useI18n()
</script>

<template>
  <ul class="divide-y divide-(--border) rounded-theme border border-(--border)" data-testid="lending-product-kinds">
    <li v-for="kind in kinds" :key="kind.artId ?? 'copies'" class="flex flex-wrap items-start gap-3 p-3">
      <PieceThumb v-bind="offerLook({stationId: stationUid, pictureKey: kind.pictureKey, icon: kind.icon,
                                     color: kind.color, homogeneous: true}, 128)"/>
      <div class="min-w-0 flex-1 space-y-1">
        <p class="font-medium">{{ kind.name || productName }}</p>
        <ProductDescription :text="kind.description" folded/>
        <p class="text-sm font-medium" :class="kind.free > 0 ? 'text-success' : 'text-(--text-muted)'">
          {{ t('lendingShop.free', {count: kind.free}) }}
        </p>
      </div>
      <slot name="action" :kind="kind"/>
    </li>
  </ul>
</template>

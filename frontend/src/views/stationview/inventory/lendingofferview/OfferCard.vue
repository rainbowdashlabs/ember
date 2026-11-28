/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AppLink from '@/components/navigation/AppLink.vue'
import type {OfferProduct} from '@/api/generated/schema'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useRouter} from 'vue-router'
import OfferRowItem from './OfferRowItem.vue'
import {rowsOf, type OfferRow} from './offerRows'

/**
 * One product of the offer page: its row, a row per kind, and in an inventory of different things a row
 * per piece. In an inventory of copies single pieces keep their rules on the inventory's page.
 */
const props = defineProps<{
  product: OfferProduct
  selected: ReadonlySet<string>
  editable: boolean
  /** Whether the card is only shown, as in the help centre. */
  readonly?: boolean
}>()

const emit = defineEmits<{
  toggle: [key: string, selected: boolean]
  edit: [row: OfferRow]
  changed: []
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()
const router = useRouter()
const rows = computed(() => rowsOf(props.product))
const counts = computed(() => new Map<string, number>([
  [`inventory:${props.product.inventoryId}`, props.product.pieceCount],
  ...props.product.kinds.map(kind => [`art:${kind.artId}`, kind.pieceCount] as [string, number]),
]))
const inventoryHref = computed(() => routes.detail
    ? router.resolve({name: routes.detail, params: {id: props.product.inventoryId}}).href
    : '')
</script>

<template>
  <section class="rounded-theme border border-(--border) px-3" data-testid="lending-offer-card">
    <ul class="divide-y divide-(--border)">
      <OfferRowItem v-for="row in rows" :key="row.key" :row="row" :count="counts.get(row.key)"
                      :editable="editable" :readonly="readonly" :selected="selected.has(row.key)" @update:selected="value => emit('toggle', row.key, value)"
                      @edit="emit('edit', row)" @changed="emit('changed')"/>
    </ul>
    <p v-if="product.homogeneous && inventoryHref" class="py-2 text-xs">
      <AppLink :href="inventoryHref">{{ t('lendingOffer.singlePieces') }}</AppLink>
    </p>
  </section>
</template>

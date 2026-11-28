/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import * as lending from '@/api/lending'
import type {ProductDetail, RemotePiece} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useShopQuery} from './lendingview/useShopQuery'
import AvailabilityCalendar from './lendingproductview/AvailabilityCalendar.vue'
import KindRows from './lendingproductview/KindRows.vue'
import PieceList from './lendingproductview/PieceList.vue'
import PiecePanel from './lendingproductview/PiecePanel.vue'
import ProductHeader from './lendingproductview/ProductHeader.vue'
import AddCountToCart from './lendingproductview/AddCountToCart.vue'
import AddPieceToCart from './lendingproductview/AddPieceToCart.vue'
import {useLendingCart} from '@/composables/useLendingCart'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const routes = useInventoryRoutes()
const {dateFrom, dateTo, setDays} = useShopQuery()

const stationUid = computed(() => String(route.params.stationId ?? ''))
const inventoryId = computed(() => Number(route.params.inventoryId))
const detail = ref<ProductDetail | null>(null)
const cart = useLendingCart()
onMounted(cart.ensureLoaded)

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  const answer = await lending.getProduct(stationUid.value, inventoryId.value, {
    from: dateFrom.value || undefined,
    to: dateTo.value || undefined,
  })
  if (isCurrent()) detail.value = answer
})
watch([dateFrom, dateTo, stationUid, inventoryId], () => reload())

const opened = ref<RemotePiece | null>(null)
const panelOpen = computed({
  get: () => opened.value !== null,
  set: (value: boolean) => { if (!value) opened.value = null },
})

const calendarRows = computed(() => {
  const product = detail.value?.product
  if (!product) return []
  const rows = product.kinds.map(kind => ({key: kind.artId == null ? 'copies' : String(kind.artId), label: kind.name || product.name}))
  if (product.pieces.length > 0) rows.push({key: 'singles', label: t('lendingCalendar.singles')})
  return rows
})

function backToShop() {
  void router.push({name: routes.lending, query: route.query})
}
</script>

<template>
  <ViewContent :title="detail?.product.name ?? t('pages.inventory-lending-product.title')"
               :subtitle="t('pages.inventory-lending-product.subtitle')">
    <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4" @click="backToShop">{{ t('lendingProduct.back') }}</SecondaryButton>
    <AsyncSection :failure="failure" :loading="loading && !detail">
      <div v-if="detail" class="space-y-6">
        <ProductHeader :detail="detail" :station-in-cart="cart.stations.value.has(detail.stationId)"/>
        <MutedText v-if="!dateFrom" tag="p">{{ t('lendingShop.todayHint') }}</MutedText>
        <section v-if="detail.product.kinds.length > 0" class="space-y-2">
          <SubHeader>{{ t('lendingProduct.kinds') }}</SubHeader>
          <KindRows :kinds="detail.product.kinds" :station-uid="detail.stationId" :product-name="detail.product.name">
            <template #action="{kind}">
              <AddCountToCart :station-id="detail.stationId" :product="detail.product" :kind="kind"/>
            </template>
          </KindRows>
        </section>
        <section v-if="detail.product.pieces.length > 0" class="space-y-2">
          <SubHeader>{{ t('lendingProduct.pieces') }}</SubHeader>
          <PieceList :pieces="detail.product.pieces" :station-uid="detail.stationId" :windowed="!!dateFrom" @open="opened = $event">
            <template #action="{piece}">
              <AddPieceToCart :station-id="detail.stationId" :product="detail.product" :piece="piece"/>
            </template>
          </PieceList>
        </section>
        <AvailabilityCalendar :date-from="dateFrom" :date-to="dateTo" :station-uid="detail.stationId"
                              :inventory-id="detail.product.inventoryId" :rows="calendarRows" @pick="setDays"/>
      </div>
    </AsyncSection>
    <PiecePanel v-if="opened && detail" v-model="panelOpen" :date-from="dateFrom" :date-to="dateTo"
                :piece="opened" :station-uid="detail.stationId" :inventory-id="detail.product.inventoryId" @pick="setDays"/>
  </ViewContent>
</template>

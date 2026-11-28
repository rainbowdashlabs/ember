/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import QuantityStepper from '@/components/input/number/QuantityStepper.vue'
import type {ProductDetail} from '@/api/generated/schema'
import CalendarLegend from '@/views/stationview/inventory/lendingproductview/CalendarLegend.vue'
import KindRows from '@/views/stationview/inventory/lendingproductview/KindRows.vue'
import PieceList from '@/views/stationview/inventory/lendingproductview/PieceList.vue'
import ProductHeader from '@/views/stationview/inventory/lendingproductview/ProductHeader.vue'

const {t} = useI18n()

const sample = computed<ProductDetail>(() => ({
  stationId: '00000000-0000-0000-0000-000000000001',
  stationName: t('helpCenter.inventoryLendingProduct.sampleStation'),
  distanceKm: 4.2,
  product: {
    inventoryId: 1,
    name: t('helpCenter.inventoryLendingProduct.sampleProduct'),
    description: t('helpCenter.inventoryLendingProduct.sampleDescription'),
    pictureKey: null, icon: null, color: null,
    address: {addressLine: 'Wachweg 1', postalCode: '12345', city: 'Nordstadt', country: 'DE'},
    kinds: [
      {artId: 1, name: t('helpCenter.inventoryLendingProduct.sampleKind'), description: '', pictureKey: null, icon: null, color: null, free: 4},
    ],
    pieces: [
      {itemId: 1, name: t('helpCenter.inventoryLendingProduct.samplePiece'), description: '', pictureKey: null, free: true},
      {itemId: 2, name: t('helpCenter.inventoryLendingProduct.samplePieceTaken'), description: '', pictureKey: null, free: false},
    ],
  },
}))
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingProduct.title')" :subtitle="t('helpCenter.inventoryLendingProduct.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingProduct.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingProduct.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingProduct.whereText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingProduct.howTo')">
      <p>{{ t('helpCenter.inventoryLendingProduct.kindsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingProduct.piecesText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingProduct.cartText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingProduct.exampleTitle')">
      <NeutralContainer class="space-y-4">
        <SecondaryButton :icon="['fas', 'chevron-left']">{{ t('lendingProduct.back') }}</SecondaryButton>
        <ProductHeader :detail="sample" station-in-cart/>
        <KindRows :kinds="sample.product.kinds" :station-uid="sample.stationId" :product-name="sample.product.name">
          <template #action>
            <div class="flex items-center gap-2">
              <QuantityStepper :model-value="2" :max="4" :label="t('lendingCart.quantityOf', {name: sample.product.kinds[0]?.name ?? ''})"/>
              <PrimaryButton :icon="['fas', 'cart-shopping']">{{ t('lendingCart.add') }}</PrimaryButton>
            </div>
          </template>
        </KindRows>
        <PieceList :pieces="sample.product.pieces" :station-uid="sample.stationId" :windowed="false">
          <template #action="{piece}">
            <SecondaryButton :icon="['fas', 'cart-shopping']" :disabled="!piece.free">{{ t('lendingCart.add') }}</SecondaryButton>
          </template>
        </PieceList>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingProduct.calendarTitle')">
      <p>{{ t('helpCenter.inventoryLendingProduct.calendarText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingProduct.calendarPick') }}</p>
      <NeutralContainer>
        <CalendarLegend/>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingProduct.tip') }}</HelpTip>
  </HelpArticle>
</template>

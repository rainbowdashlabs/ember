/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {ProductSummary} from '@/api/generated/schema'
import ProductTile from '@/views/stationview/inventory/lendingshopview/ProductTile.vue'
import ShopFilters from '@/views/stationview/inventory/lendingshopview/ShopFilters.vue'

const {t} = useI18n()

function sample(name: string, stationName: string, kinds: number, pieces: number, free: number, distanceKm: number): ProductSummary {
  return {
    stationId: '00000000-0000-0000-0000-000000000001', stationName, distanceKm,
    product: {inventoryId: kinds + pieces, name, teaser: '', pictureKey: null, icon: null, color: null, kinds, pieces, free, matched: [], rank: 0},
  }
}

const tiles = [
  sample('Funkgeräte', 'DLRG Musterstadt', 2, 0, 6, 4.2),
  sample('Spiele', 'DLRG Neustadt', 0, 12, 9, 7.8),
  sample('Zelte', 'DLRG Musterstadt', 1, 0, 2, 4.2),
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingShop.title')" :subtitle="t('helpCenter.inventoryLendingShop.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingShop.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingShop.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingShop.searchText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShop.howTo')">
      <p>{{ t('helpCenter.inventoryLendingShop.daysText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingShop.filterText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingShop.tileText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShop.exampleTitle')">
      <NeutralContainer class="space-y-4">
        <ShopFilters search="" date-from="" date-to="" station="" sort="best" :only-in-cart="false"
                     :stations="[{id: 'a', name: 'DLRG Musterstadt'}, {id: 'b', name: 'DLRG Neustadt'}]"/>
        <ul class="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <li v-for="(tile, index) in tiles" :key="tile.product.name">
            <ProductTile :summary="tile" link="" :in-cart="index === 0 ? 2 : 0" :station-in-cart="index === 2"/>
          </li>
        </ul>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingShop.tip') }}</HelpTip>
  </HelpArticle>
</template>

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
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {CartLineView} from '@/api/generated/schema'
import CartDetailsForm from '@/views/stationview/inventory/lendingcartview/CartDetailsForm.vue'
import CartLineRow from '@/views/stationview/inventory/lendingcartview/CartLineRow.vue'

const {t} = useI18n()

const lines = computed<CartLineView[]>(() => [
  {id: 1, stationId: '00000000-0000-0000-0000-000000000001', stationName: t('helpCenter.inventoryLendingCart.sampleStation'),
    inventoryId: 1, artId: 1, itemId: null, quantity: 3, inventoryName: t('helpCenter.inventoryLendingCart.sampleInventory'),
    lineName: t('helpCenter.inventoryLendingCart.sampleKind'), pictureKey: null},
  {id: 2, stationId: '00000000-0000-0000-0000-000000000001', stationName: t('helpCenter.inventoryLendingCart.sampleStation'),
    inventoryId: 2, artId: null, itemId: 5, quantity: 1, inventoryName: t('helpCenter.inventoryLendingCart.sampleGames'),
    lineName: t('helpCenter.inventoryLendingCart.samplePiece'), pictureKey: null},
])
const details = computed(() => ({
  dateFrom: '2026-12-12', dateTo: '2026-12-14', occasion: t('helpCenter.inventoryLendingCart.sampleOccasion'), note: '',
}))
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingCart.title')" :subtitle="t('helpCenter.inventoryLendingCart.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingCart.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingCart.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCart.sharedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingCart.howTo')">
      <p>{{ t('helpCenter.inventoryLendingCart.linesText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCart.checkText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingCart.sendText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingCart.exampleTitle')">
      <NeutralContainer class="space-y-4">
        <div class="flex flex-wrap items-center gap-2">
          <SubHeader>{{ t('lendingCartPage.requestTo') }}</SubHeader>
          <StationBadge :station-name="lines[0]?.stationName ?? ''"/>
        </div>
        <ul class="divide-y divide-(--border) rounded-theme border border-(--border)">
          <CartLineRow :line="lines[0]!" :check="{lineId: 1, free: 2, state: 'SHORT'}"/>
          <CartLineRow :line="lines[1]!" :check="{lineId: 2, free: 1, state: 'FREE'}"/>
        </ul>
        <SubHeader>{{ t('lendingCartPage.forAll') }}</SubHeader>
        <CartDetailsForm :details="details"/>
        <MutedText tag="p">{{ t('lendingCartPage.needsFree') }}</MutedText>
        <ButtonRow align="end">
          <SecondaryButton :icon="['fas', 'rotate']">{{ t('lendingCartPage.recheck') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']" disabled>{{ t('lendingCartPage.sendOne') }}</PrimaryButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingCart.againTitle')">
      <p>{{ t('helpCenter.inventoryLendingCart.againText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingCart.tip') }}</HelpTip>
  </HelpArticle>
</template>

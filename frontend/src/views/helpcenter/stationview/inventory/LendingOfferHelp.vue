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
import type {CatalogProduct} from '@/api/generated/schema'
import CatalogCard from '@/views/stationview/inventory/lendingofferview/CatalogCard.vue'

const {t} = useI18n()

const sample: CatalogProduct = {
  inventoryId: 1, name: 'Funkgeräte', description: 'Handfunkgeräte für Übungen.', pictureKey: null, icon: null, color: null,
  homogeneous: false, pieceCount: 14, share: {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []}, offered: true, visible: null,
  kinds: [
    {artId: 1, name: 'FuG 8b', description: '', pictureKey: null, pieceCount: 10, share: null, visible: null},
    {artId: 2, name: 'Koffer', description: '', pictureKey: null, pieceCount: 2, share: {grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []}, visible: null},
  ],
  pieces: [],
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingOffer.title')" :subtitle="t('helpCenter.inventoryLendingOffer.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingOffer.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingOffer.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingOffer.shareText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.inventoryLendingOffer.howTo')">
      <p>{{ t('helpCenter.inventoryLendingOffer.textText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingOffer.bulkText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingOffer.viewAsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.inventoryLendingOffer.exampleTitle')">
      <NeutralContainer>
        <CatalogCard :product="sample" :selected="new Set()" :editable="false" readonly/>
      </NeutralContainer>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.inventoryLendingOffer.tip') }}</HelpTip>
  </HelpArticle>
</template>

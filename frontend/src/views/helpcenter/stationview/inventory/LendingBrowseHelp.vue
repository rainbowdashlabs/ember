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
import LendingOfferFilters from '@/views/stationview/inventory/lendingview/LendingOfferFilters.vue'
import LendingOfferList from '@/views/stationview/inventory/lendingview/LendingOfferList.vue'
import {offers} from './lendinghelp/fixtures'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingBrowse.title')" :subtitle="t('helpCenter.inventoryLendingBrowse.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingBrowse.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingBrowse.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBrowse.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBrowse.howTo')">
      <p>{{ t('helpCenter.inventoryLendingBrowse.step1') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBrowse.step2') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBrowse.step3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBrowse.dummyTitle')">
      <NeutralContainer class="space-y-4">
        <LendingOfferFilters search-query="" date-from="" date-to=""/>
        <LendingOfferList :items="offers"/>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingBrowse.tip') }}</HelpTip>
  </HelpArticle>
</template>

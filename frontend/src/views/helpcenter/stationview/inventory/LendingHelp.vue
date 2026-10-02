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
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import LendingTabs from '@/views/stationview/inventory/lendingview/LendingTabs.vue'
import LendingOfferFilters from '@/views/stationview/inventory/lendingview/LendingOfferFilters.vue'
import LendingOfferList from '@/views/stationview/inventory/lendingview/LendingOfferList.vue'
import LendingRequestList from '@/views/stationview/inventory/lendingview/LendingRequestList.vue'
import {provideInventoryRoutes, STATION_INVENTORY_ROUTES} from '@/composables/useInventoryRoutes'
import {incomingRequests, offers, outgoingRequests} from './lendinghelp/fixtures'

const {t} = useI18n()

provideInventoryRoutes({...STATION_INVENTORY_ROUTES, lendingRequest: undefined})
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLending.title')" :subtitle="t('helpCenter.inventoryLending.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLending.whatIs')">
      <p>{{ t('helpCenter.inventoryLending.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.otherInstanceTitle')">
      <p>{{ t('helpCenter.inventoryLending.otherInstanceText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.otherInstanceText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.tabsTitle')">
      <p>{{ t('helpCenter.inventoryLending.tabsOffersText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.tabsRequestsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.dummyOffersTitle')">
      <NeutralContainer class="space-y-4">
        <ButtonRow>
          <PrimaryButton :icon="['fas', 'calendar-xmark']">{{ t('lending.blocks') }}</PrimaryButton>
          <SecondaryButton :icon="['fas', 'share-nodes']">{{ t('lendingShare.overview') }}</SecondaryButton>
        </ButtonRow>
        <LendingTabs model-value="offers"/>
        <LendingOfferFilters search-query="" date-from="" date-to=""/>
        <LendingOfferList :items="offers"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.dummyRequestsTitle')">
      <p>{{ t('helpCenter.inventoryLending.requestsExplanation') }}</p>
      <NeutralContainer class="space-y-4">
        <LendingTabs model-value="requests"/>
        <SubHeader>{{ t('lending.incoming') }}</SubHeader>
        <LendingRequestList :entries="incomingRequests" direction="incoming"/>
        <SubHeader>{{ t('lending.outgoing') }}</SubHeader>
        <LendingRequestList :entries="outgoingRequests" direction="outgoing"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.statusTitle')">
      <p>{{ t('helpCenter.inventoryLending.statusRequestedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusApprovedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusLentDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusReturnedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusDeclinedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusClosedDesc') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLending.tip') }}</HelpTip>
  </HelpArticle>
</template>

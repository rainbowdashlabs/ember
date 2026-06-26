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
import DummyOffersPanel from './lendinghelp/DummyOffersPanel.vue'
import DummyRequestsPanel from './lendinghelp/DummyRequestsPanel.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLending.title')" :subtitle="t('helpCenter.inventoryLending.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLending.whatIs')">
      <p>{{ t('helpCenter.inventoryLending.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.tabsTitle')">
      <p>{{ t('helpCenter.inventoryLending.tabsOffersText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.tabsRequestsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.dummyOffersTitle')">
      <DummyOffersPanel/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.dummyRequestsTitle')">
      <p>{{ t('helpCenter.inventoryLending.requestsExplanation') }}</p>
      <DummyRequestsPanel/>
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

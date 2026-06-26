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
import DummyBlocksList from '@/views/helpcenter/stationview/inventory/lendingblockshelp/DummyBlocksList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingBlocks.title')" :subtitle="t('helpCenter.inventoryLendingBlocks.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.createTitle')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep1') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep2') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.dummyTitle')">
      <DummyBlocksList />
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.badgeTitle')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.badgeFullText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.badgeInventoryText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingBlocks.tip') }}</HelpTip>
  </HelpArticle>
</template>

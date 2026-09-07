/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpList from '@/components/helpcenter/HelpList.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle
      :subtitle="t('helpCenter.inventoryLendingShares.subtitle')"
      :title="t('helpCenter.inventoryLendingShares.title')"
  >
    <HelpSection :title="t('helpCenter.inventoryLendingShares.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingShares.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingShares.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShares.narrowestTitle')">
      <p>{{ t('helpCenter.inventoryLendingShares.narrowestText') }}</p>
      <HelpList>
        <li>{{ t('helpCenter.inventoryLendingShares.narrowestItem') }}</li>
        <li>{{ t('helpCenter.inventoryLendingShares.narrowestArt') }}</li>
        <li>{{ t('helpCenter.inventoryLendingShares.narrowestInventory') }}</li>
        <li>{{ t('helpCenter.inventoryLendingShares.narrowestNone') }}</li>
      </HelpList>
      <p>{{ t('helpCenter.inventoryLendingShares.narrowestPartner') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShares.whereTitle')">
      <p>{{ t('helpCenter.inventoryLendingShares.whereText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingShares.whereText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShares.capabilityTitle')">
      <p>{{ t('helpCenter.inventoryLendingShares.capabilityText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShares.runningTitle')">
      <p>{{ t('helpCenter.inventoryLendingShares.runningText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingShares.emptyTitle')">
      <p>{{ t('helpCenter.inventoryLendingShares.emptyText') }}</p>
      <HelpList>
        <li>{{ t('helpCenter.inventoryLendingShares.emptyNothingShared') }}</li>
        <li>{{ t('helpCenter.inventoryLendingShares.emptyNothingFree') }}</li>
      </HelpList>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLendingShares.tip') }}</HelpTip>
  </HelpArticle>
</template>

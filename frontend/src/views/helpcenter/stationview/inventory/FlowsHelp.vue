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

const {t} = useI18n()
</script>

<template>
  <HelpArticle :subtitle="t('helpCenter.inventoryFlows.subtitle')" :title="t('helpCenter.inventoryFlows.title')">
    <HelpSection :title="t('helpCenter.inventoryFlows.whatIs')">
      <p>{{ t('helpCenter.inventoryFlows.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryFlows.shapeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryFlows.whichApplies')">
      <p>{{ t('helpCenter.inventoryFlows.whichAppliesText') }}</p>
      <p>{{ t('helpCenter.inventoryFlows.ownerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryFlows.editing')">
      <p>{{ t('helpCenter.inventoryFlows.editingText') }}</p>
      <p>{{ t('helpCenter.inventoryFlows.orderText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryFlows.problemsTitle')">
      <p>{{ t('helpCenter.inventoryFlows.problemsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryFlows.tip') }}</HelpTip>
  </HelpArticle>
</template>

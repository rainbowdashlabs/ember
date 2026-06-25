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
import DummyRequirementsList from '@/views/helpcenter/stationview/inventory/requirementshelp/DummyRequirementsList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryRequirements.title')" :subtitle="t('helpCenter.inventoryRequirements.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryRequirements.whatIs')">
      <p>{{ t('helpCenter.inventoryRequirements.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryRequirements.createTitle')">
      <p>{{ t('helpCenter.inventoryRequirements.createRoleOrGroup') }}</p>
      <p>{{ t('helpCenter.inventoryRequirements.createInventory') }}</p>
      <p>{{ t('helpCenter.inventoryRequirements.createQuantity') }}</p>
    </HelpSection>

    <DummyRequirementsList/>

    <HelpTip>{{ t('helpCenter.inventoryRequirements.tip') }}</HelpTip>
  </HelpArticle>
</template>

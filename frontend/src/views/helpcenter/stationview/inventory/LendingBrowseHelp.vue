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
import DummyAvailableTable from '@/views/helpcenter/stationview/inventory/lendingbrowsehelp/DummyAvailableTable.vue'

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

    <DummyAvailableTable/>

    <HelpTip>{{ t('helpCenter.inventoryLendingBrowse.tip') }}</HelpTip>
  </HelpArticle>
</template>

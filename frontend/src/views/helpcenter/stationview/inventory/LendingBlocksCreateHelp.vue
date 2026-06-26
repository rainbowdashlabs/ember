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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DummyDateRangePanel from '@/views/helpcenter/stationview/inventory/lendingblockscreatehelp/DummyDateRangePanel.vue'
import DummyScopePicker from '@/views/helpcenter/stationview/inventory/lendingblockscreatehelp/DummyScopePicker.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.lendingBlocksCreate.title')" :subtitle="t('helpCenter.lendingBlocksCreate.subtitle')">
    <HelpSection :title="t('helpCenter.lendingBlocksCreate.whatIs')">
      <p>{{ t('helpCenter.lendingBlocksCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingBlocksCreate.howTo')">
      <p>{{ t('helpCenter.lendingBlocksCreate.step1') }}</p>
      <p>{{ t('helpCenter.lendingBlocksCreate.step2') }}</p>
      <p>{{ t('helpCenter.lendingBlocksCreate.step3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingBlocksCreate.dateTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.dateText') }}</p>
      <DummyDateRangePanel/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingBlocksCreate.scopeTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.scopeText') }}</p>
      <DummyScopePicker/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingBlocksCreate.actionsTitle')">
      <p>{{ t('helpCenter.lendingBlocksCreate.actionsText') }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.lendingBlocksCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>

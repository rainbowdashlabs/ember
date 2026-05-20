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
  <HelpArticle :title="t('helpCenter.inventoryModuleOverview.overviewTitle')" :subtitle="t('helpCenter.inventoryModuleOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.inventoryModuleOverview.whatIs')">
      <p>{{ t('helpCenter.inventoryModuleOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryModuleOverview.pagesTitle')">
      <p>{{ t('helpCenter.inventoryModuleOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryModuleOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>

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
  <HelpArticle :title="t('helpCenter.protocolModuleOverview.title')" :subtitle="t('helpCenter.protocolModuleOverview.subtitle')">
    <HelpSection :title="t('helpCenter.protocolModuleOverview.whatIs')">
      <p>{{ t('helpCenter.protocolModuleOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolModuleOverview.pagesTitle')">
      <p>{{ t('helpCenter.protocolModuleOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolModuleOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>

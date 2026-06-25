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
import ApiStatusExample from './apistatushelp/ApiStatusExample.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminApiStatus.title')" :subtitle="t('helpCenter.adminApiStatus.subtitle')">
    <HelpSection :title="t('helpCenter.adminApiStatus.whatIs')">
      <p>{{ t('helpCenter.adminApiStatus.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.summaryTitle')">
      <p>{{ t('helpCenter.adminApiStatus.summaryText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.metricsTitle')">
      <p>{{ t('helpCenter.adminApiStatus.metricsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.tabsTitle')">
      <p>{{ t('helpCenter.adminApiStatus.tabsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.exampleTitle')">
      <ApiStatusExample/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApiStatus.tip') }}</HelpTip>
  </HelpArticle>
</template>

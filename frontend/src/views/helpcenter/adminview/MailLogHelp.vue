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
  <HelpArticle :title="t('helpCenter.adminMailLog.title')" :subtitle="t('helpCenter.adminMailLog.subtitle')">
    <HelpSection :title="t('helpCenter.adminMailLog.whatIs')">
      <p>{{ t('helpCenter.adminMailLog.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.statesTitle')">
      <p>{{ t('helpCenter.adminMailLog.statesText') }}</p>
      <p>{{ t('helpCenter.adminMailLog.deliveryText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.stuckTitle')">
      <p>{{ t('helpCenter.adminMailLog.stuckText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMailLog.providersTitle')">
      <p>{{ t('helpCenter.adminMailLog.providersText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminMailLog.tip') }}</HelpTip>
  </HelpArticle>
</template>

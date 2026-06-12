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
  <HelpArticle :title="t('helpCenter.adminDataTracking.title')" :subtitle="t('helpCenter.adminDataTracking.subtitle')">
    <HelpSection :title="t('helpCenter.adminDataTracking.whatIs')">
      <p>{{ t('helpCenter.adminDataTracking.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDataTracking.statusesTitle')">
      <p>{{ t('helpCenter.adminDataTracking.statusesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDataTracking.strategiesTitle')">
      <p>{{ t('helpCenter.adminDataTracking.strategiesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminDataTracking.tip') }}</HelpTip>
  </HelpArticle>
</template>

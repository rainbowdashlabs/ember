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

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.stationInsights.title')" :subtitle="t('helpCenter.stationInsights.subtitle')">
    <HelpSection :title="t('helpCenter.stationInsights.whatIs')">
      <p>{{ t('helpCenter.stationInsights.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationInsights.privacyTitle')">
      <p>{{ t('helpCenter.stationInsights.privacyText') }}</p>
      <HelpList :items="[
        t('helpCenter.stationInsights.privacyNoIp'),
        t('helpCenter.stationInsights.privacyNoCookies'),
        t('helpCenter.stationInsights.privacyOnlyCountry'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationInsights.botsTitle')">
      <p>{{ t('helpCenter.stationInsights.botsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationInsights.controlsTitle')">
      <p>{{ t('helpCenter.stationInsights.controlsText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

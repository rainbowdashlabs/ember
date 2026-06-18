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
  <HelpArticle :title="t('helpCenter.stationTraffic.title')" :subtitle="t('helpCenter.stationTraffic.subtitle')">
    <HelpSection :title="t('helpCenter.stationTraffic.whatIs')">
      <p>{{ t('helpCenter.stationTraffic.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationTraffic.bucketsTitle')">
      <p>{{ t('helpCenter.stationTraffic.bucketsText') }}</p>
      <HelpList :items="[
        t('helpCenter.stationTraffic.bucketAuthenticated'),
        t('helpCenter.stationTraffic.bucketUnauthenticated'),
        t('helpCenter.stationTraffic.bucketFederation'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationTraffic.controlsTitle')">
      <p>{{ t('helpCenter.stationTraffic.controlsText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

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
import HelpTip from '@/components/helpcenter/HelpTip.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminTraffic.title')" :subtitle="t('helpCenter.adminTraffic.subtitle')">
    <HelpSection :title="t('helpCenter.adminTraffic.whatIs')">
      <p>{{ t('helpCenter.adminTraffic.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTraffic.bucketsTitle')">
      <p>{{ t('helpCenter.adminTraffic.bucketsText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminTraffic.bucketAuthenticated'),
        t('helpCenter.adminTraffic.bucketUnauthenticated'),
        t('helpCenter.adminTraffic.bucketFederation'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTraffic.controlsTitle')">
      <p>{{ t('helpCenter.adminTraffic.controlsText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminTraffic.controlsWindow'),
        t('helpCenter.adminTraffic.controlsMetric'),
        t('helpCenter.adminTraffic.controlsBucket'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTraffic.leaderboardTitle')">
      <p>{{ t('helpCenter.adminTraffic.leaderboardText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminTraffic.tip') }}</HelpTip>
  </HelpArticle>
</template>

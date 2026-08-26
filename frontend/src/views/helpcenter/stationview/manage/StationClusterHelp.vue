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
  <HelpArticle :subtitle="t('helpCenter.stationCluster.subtitle')" :title="t('helpCenter.stationCluster.title')">
    <HelpSection :title="t('helpCenter.stationCluster.whatTitle')">
      <p>{{ t('helpCenter.stationCluster.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationCluster.applyTitle')">
      <p>{{ t('helpCenter.stationCluster.applyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationCluster.withdrawTitle')">
      <p>{{ t('helpCenter.stationCluster.withdrawText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationCluster.leaveTitle')">
      <p>{{ t('helpCenter.stationCluster.leaveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.stationCluster.tip') }}</HelpTip>
  </HelpArticle>
</template>

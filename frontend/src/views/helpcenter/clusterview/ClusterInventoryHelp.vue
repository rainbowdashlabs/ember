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
  <HelpArticle :subtitle="t('helpCenter.clusterInventory.subtitle')" :title="t('helpCenter.clusterInventory.title')">
    <HelpSection :title="t('helpCenter.clusterInventory.whatTitle')">
      <p>{{ t('helpCenter.clusterInventory.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventory.usesTitle')">
      <p>{{ t('helpCenter.clusterInventory.usesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventory.stationLimitsTitle')">
      <p>{{ t('helpCenter.clusterInventory.stationLimitsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterInventory.tip') }}</HelpTip>
  </HelpArticle>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryStatistics.subtitle')" :title="t('helpCenter.clusterInventoryStatistics.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryStatistics.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryStatistics.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryStatistics.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryStatistics.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

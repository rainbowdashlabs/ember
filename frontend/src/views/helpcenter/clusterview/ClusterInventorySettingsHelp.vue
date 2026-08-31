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
  <HelpArticle :subtitle="t('helpCenter.clusterInventorySettings.subtitle')"
               :title="t('helpCenter.clusterInventorySettings.title')">
    <HelpSection :title="t('helpCenter.clusterInventorySettings.moduleTitle')">
      <p>{{ t('helpCenter.clusterInventorySettings.moduleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventorySettings.flowsTitle')">
      <p>{{ t('helpCenter.clusterInventorySettings.flowsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventorySettings.lossTitle')">
      <p>{{ t('helpCenter.clusterInventorySettings.lossText') }}</p>
      <p>{{ t('helpCenter.clusterInventorySettings.lossDecideText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventorySettings.tagsTitle')">
      <p>{{ t('helpCenter.clusterInventorySettings.tagsText') }}</p>
      <p>{{ t('helpCenter.clusterInventorySettings.tagsStandsBesideText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterInventorySettings.tip') }}</HelpTip>
  </HelpArticle>
</template>

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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryItem.subtitle')" :title="t('helpCenter.clusterInventoryItem.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryItem.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryItem.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryItem.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryItem.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

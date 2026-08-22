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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryContainer.subtitle')" :title="t('helpCenter.clusterInventoryContainer.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryContainer.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryContainer.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryContainer.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryContainer.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

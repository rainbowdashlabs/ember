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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryRequirements.subtitle')" :title="t('helpCenter.clusterInventoryRequirements.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryRequirements.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryRequirements.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryRequirements.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryRequirements.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryDetail.subtitle')" :title="t('helpCenter.clusterInventoryDetail.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryDetail.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryDetail.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryDetail.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryDetail.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

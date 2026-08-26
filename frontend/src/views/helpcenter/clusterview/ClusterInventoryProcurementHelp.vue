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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryProcurement.subtitle')" :title="t('helpCenter.clusterInventoryProcurement.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryProcurement.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryProcurement.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryProcurement.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryProcurement.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

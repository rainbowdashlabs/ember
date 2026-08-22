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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryChecks.subtitle')" :title="t('helpCenter.clusterInventoryChecks.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryChecks.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryChecks.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryChecks.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryChecks.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

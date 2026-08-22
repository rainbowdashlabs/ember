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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryStorage.subtitle')" :title="t('helpCenter.clusterInventoryStorage.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryStorage.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryStorage.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryStorage.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryStorage.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

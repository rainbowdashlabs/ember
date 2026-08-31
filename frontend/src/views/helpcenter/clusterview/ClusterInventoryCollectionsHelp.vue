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
  <HelpArticle
      :title="t('helpCenter.clusterInventoryCollections.title')"
      :subtitle="t('helpCenter.clusterInventoryCollections.subtitle')"
  >
    <HelpSection :title="t('helpCenter.clusterInventoryCollections.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryCollections.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryCollections.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryCollections.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

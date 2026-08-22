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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryCheckWalk.subtitle')" :title="t('helpCenter.clusterInventoryCheckWalk.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryCheckWalk.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryCheckWalk.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryCheckWalk.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryCheckWalk.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

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
  <HelpArticle :subtitle="t('helpCenter.clusterNews.subtitle')" :title="t('helpCenter.clusterNews.title')">
    <HelpSection :title="t('helpCenter.clusterNews.whatTitle')">
      <p>{{ t('helpCenter.clusterNews.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNews.reachTitle')">
      <p>{{ t('helpCenter.clusterNews.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNews.authorTitle')">
      <p>{{ t('helpCenter.clusterNews.authorText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterNews.tip') }}</HelpTip>
  </HelpArticle>
</template>

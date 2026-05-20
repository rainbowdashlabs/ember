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
  <HelpArticle :title="t('helpCenter.newsOverview.overviewTitle')" :subtitle="t('helpCenter.newsOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.newsOverview.whatIs')">
      <p>{{ t('helpCenter.newsOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsOverview.pagesTitle')">
      <p>{{ t('helpCenter.newsOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.newsOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>

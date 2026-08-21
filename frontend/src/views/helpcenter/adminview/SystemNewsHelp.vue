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
  <HelpArticle :title="t('helpCenter.adminSystemNews.title')" :subtitle="t('helpCenter.adminSystemNews.subtitle')">
    <HelpSection :title="t('helpCenter.adminSystemNews.whatIs')">
      <p>{{ t('helpCenter.adminSystemNews.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminSystemNews.authorText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSystemNews.audienceTitle')">
      <p>{{ t('helpCenter.adminSystemNews.audienceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSystemNews.notifyTitle')">
      <p>{{ t('helpCenter.adminSystemNews.notifyText') }}</p>
      <HelpTip>{{ t('helpCenter.adminSystemNews.notifyTip') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSystemNews.commentsTitle')">
      <p>{{ t('helpCenter.adminSystemNews.commentsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSystemNews.correctTitle')">
      <p>{{ t('helpCenter.adminSystemNews.correctText') }}</p>
      <p>{{ t('helpCenter.adminSystemNews.retractText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>

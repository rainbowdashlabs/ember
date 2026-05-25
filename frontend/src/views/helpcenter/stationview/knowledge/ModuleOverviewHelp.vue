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
  <HelpArticle :title="t('helpCenter.knowledgeModuleOverview.overviewTitle')" :subtitle="t('helpCenter.knowledgeModuleOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.knowledgeModuleOverview.whatIs')">
      <p>{{ t('helpCenter.knowledgeModuleOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.knowledgeModuleOverview.pagesTitle')">
      <p>{{ t('helpCenter.knowledgeModuleOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.knowledgeModuleOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>

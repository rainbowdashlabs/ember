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

/**
 * The list of documents generated from templates: what it says about each document and how to narrow it.
 */
const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.generatedDocuments.title')" :subtitle="t('helpCenter.generatedDocuments.subtitle')">
    <HelpSection :title="t('helpCenter.generatedDocuments.whatIs')">
      <p>{{ t('helpCenter.generatedDocuments.whatIsText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.versionText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.rightsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.generatedDocuments.howTo')">
      <p>{{ t('helpCenter.generatedDocuments.howToText') }}</p>
      <p>{{ t('helpCenter.generatedDocuments.goneText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.generatedDocuments.tip') }}</HelpTip>
  </HelpArticle>
</template>
